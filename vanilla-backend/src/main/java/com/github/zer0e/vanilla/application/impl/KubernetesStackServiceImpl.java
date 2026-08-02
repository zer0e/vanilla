package com.github.zer0e.vanilla.application.impl;

import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.KubernetesStackService;
import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.support.RuntimeStateResolver;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.DeployPreviewVo;
import com.github.zer0e.vanilla.application.vo.ServiceStatusVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.domain.ClusterType;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.domain.Env;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ClusterMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.VolumeMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import com.github.zer0e.vanilla.infrastructure.kubernetes.KubernetesClientFactory;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategy;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kubernetes 运行时实现：**一个栈 = 一个命名空间**。
 *
 * <p>命名空间名取自栈名（栈在集群内唯一），Deployment / Service 名 = 服务名（栈内唯一，
 * 且因命名空间隔离不会跨栈冲突），PVC 名 = 服务名-卷名（卷名仅按服务唯一）。停止 scale=0、
 * 下架删 Deployment+Service 保留 PVC 与命名空间。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KubernetesStackServiceImpl implements KubernetesStackService {

    /**
     * NodePort 基础偏移：宿主访问端口 = 声明端口 + 30000（声明端口需 <= 2767 才会映射）
     */
    private static final int NODE_PORT_BASE = 30000;
    private static final int NODE_PORT_MAX_DECLARED = 32767 - NODE_PORT_BASE;

    // 健康检查默认参数（与 Docker HEALTHCHECK 一致）
    private static final int HEALTHCHECK_INTERVAL_SECONDS = 30;
    private static final int HEALTHCHECK_TIMEOUT_SECONDS = 10;
    private static final int HEALTHCHECK_RETRIES = 3;
    private static final int HEALTHCHECK_START_PERIOD_SECONDS = 5;

    private static final int DEFAULT_LOG_TAIL = 500;
    private static final int MAX_LOG_TAIL = 10000;

    private final KubernetesClientFactory kubernetesClientFactory;
    private final StackMapper stackMapper;
    private final ClusterMapper clusterMapper;
    private final ServiceMapper serviceMapper;
    private final PortMapper portMapper;
    private final VolumeMapper volumeMapper;
    private final HistoryService historyService;

    @Override
    public boolean isKubernetes(Integer stackId) throws BusinessException {
        StackDo stack = getStack(stackId);
        ClusterDo cluster = clusterMapper.selectById(stack.getClusterId());
        return cluster != null && ClusterType.K8S.name().equalsIgnoreCase(cluster.getType());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        KubernetesClient client = kubernetesClientFactory.getClient(getCluster(stack.getClusterId()));
        String namespace = namespaceOf(stack);
        ensureNamespace(client, stack);
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);
        try {
            for (ServiceDo service : services) {
                List<PortDo> ports = portMapper.selectPortsByServiceId(service.getId());
                List<VolumeDo> volumes = volumeMapper.selectVolumesByServiceIds(List.of(service.getId()));
                applyPvcs(client, namespace, buildPvcs(namespace, stack.getId(), service, volumes));
                applyServices(client, namespace, stack.getId(), service,
                        buildServices(namespace, stack.getId(), service, ports));
                createDeployment(client, namespace, buildDeployment(namespace, stack.getId(), service, volumes));
                log.info("k8s upsert deployment done, ns={}, stackId={}, service={}",
                        namespace, stack.getId(), service.getServiceName());
            }
            // 清理已删除服务的残留资源（Deployment + Service；PVC 保留）
            removeOrphanDeployments(client, namespace, stack.getId(), services);
            removeOrphanServices(client, namespace, stack.getId(), services);
        } catch (Exception e) {
            log.error("k8s deploy stack err, stackId={}", stack.getId(), e);
            throw new BusinessException(Constants.DEPLOY_FAIL + "：" + e.getMessage());
        }
        recordHistory(stack.getId(), "部署栈 " + stack.getStackName());
        return getStackStatus(deployStackDto);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin'," +
            "'stack_' + #deployStackDto.stackId + '_stack_member'," +
            "'stack_' + #deployStackDto.stackId + '_stack_readonly')")
    public StackStatusVo getStackStatus(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        KubernetesClient client = kubernetesClientFactory.getClient(getCluster(stack.getClusterId()));
        String namespace = namespaceOf(stack);
        List<Deployment> deployments = listStackDeployments(client, namespace, stack.getId());
        Map<String, Deployment> byService = new HashMap<>();
        for (Deployment deployment : deployments) {
            String serviceId = labelOf(deployment, Constants.SERVICE_ID_LABEL);
            if (serviceId != null) {
                byService.put(serviceId, deployment);
            }
        }
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);
        // 暴露地址：每个端口一个 SVC，按服务 id 聚合（NodePort → 节点IP:nodePort，LoadBalancer → externalIP，ClusterIP → 集群内地址）
        Map<String, List<io.fabric8.kubernetes.api.model.Service>> svcByServiceId = new HashMap<>();
        for (io.fabric8.kubernetes.api.model.Service svc : listStackServices(client, namespace, stack.getId())) {
            String sid = labelOf(svc, Constants.SERVICE_ID_LABEL);
            if (sid != null) {
                svcByServiceId.computeIfAbsent(sid, k -> new ArrayList<>()).add(svc);
            }
        }
        String nodeIp = firstNodeInternalIp(client);
        List<ServiceStatusVo> serviceStatuses = new ArrayList<>();
        for (ServiceDo service : services) {
            Deployment deployment = byService.get(String.valueOf(service.getId()));
            int specReplicas = deployment == null || deployment.getSpec() == null
                    || deployment.getSpec().getReplicas() == null
                    ? 0 : deployment.getSpec().getReplicas();
            int ready = deployment == null || deployment.getStatus() == null
                    || deployment.getStatus().getReadyReplicas() == null
                    ? 0 : deployment.getStatus().getReadyReplicas();
            // deployment 存在即视为有编排目标：缩放为 0（停止）时总数为 1，映射为 STOPPED 而非 NONE
            int total = deployment == null ? 0 : Math.max(specReplicas, 1);
            ServiceStatusVo status = new ServiceStatusVo();
            status.setServiceId(service.getId());
            status.setServiceName(service.getServiceName());
            status.setReplicas(specReplicas > 0 ? specReplicas
                    : (service.getReplicas() == null ? 1 : service.getReplicas()));
            status.setRunningCount(ready);
            status.setHealthyCount(ready);
            status.setStatus(RuntimeStateResolver.resolveStatus(total, ready));
            List<String> exposed = new ArrayList<>();
            for (io.fabric8.kubernetes.api.model.Service svc :
                    svcByServiceId.getOrDefault(String.valueOf(service.getId()), Collections.emptyList())) {
                exposed.addAll(buildK8sExposedAddresses(svc, nodeIp));
            }
            status.setExposedAddresses(exposed.stream().distinct().toList());
            serviceStatuses.add(status);
        }
        StackStatusVo result = new StackStatusVo();
        result.setStackId(stack.getId());
        result.setStatus(RuntimeStateResolver.resolveStackStatus(serviceStatuses));
        result.setServices(serviceStatuses);
        return result;
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public void stopStack(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        KubernetesClient client = kubernetesClientFactory.getClient(getCluster(stack.getClusterId()));
        String namespace = namespaceOf(stack);
        for (Deployment deployment : listStackDeployments(client, namespace, stack.getId())) {
            client.apps().deployments().inNamespace(namespace)
                    .withName(deployment.getMetadata().getName()).scale(0);
        }
        recordHistory(stack.getId(), "停止栈 " + stack.getStackName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public void removeStack(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        KubernetesClient client = kubernetesClientFactory.getClient(getCluster(stack.getClusterId()));
        String namespace = namespaceOf(stack);
        // Deployment 与 Service 删除；PVC 与命名空间保留（与 Docker named volume 下架保留的语义一致）
        for (Deployment deployment : listStackDeployments(client, namespace, stack.getId())) {
            client.apps().deployments().inNamespace(namespace)
                    .withName(deployment.getMetadata().getName()).delete();
        }
        for (io.fabric8.kubernetes.api.model.Service kubeService : client.services().inNamespace(namespace)
                .withLabel(Constants.STACK_ID_LABEL, String.valueOf(stack.getId())).list().getItems()) {
            client.services().inNamespace(namespace).withName(kubeService.getMetadata().getName()).delete();
        }
        recordHistory(stack.getId(), "下架栈 " + stack.getStackName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #containerLogsDto.stackId + '_stack_admin'," +
            "'stack_' + #containerLogsDto.stackId + '_stack_member'," +
            "'stack_' + #containerLogsDto.stackId + '_stack_readonly')")
    public ContainerLogVo getContainerLog(ContainerLogsDto containerLogsDto) throws BusinessException {
        StackDo stack = getStack(containerLogsDto.getStackId());
        KubernetesClient client = kubernetesClientFactory.getClient(getCluster(stack.getClusterId()));
        String namespace = namespaceOf(stack);
        List<Pod> pods = client.pods().inNamespace(namespace)
                .withLabel(Constants.STACK_ID_LABEL, String.valueOf(stack.getId()))
                .withLabel(Constants.SERVICE_ID_LABEL, String.valueOf(containerLogsDto.getServiceId()))
                .list().getItems();
        if (CollectionUtils.isEmpty(pods)) {
            throw new BusinessException("服务未部署，无可查看日志的容器");
        }
        List<Pod> sorted = pods.stream()
                .sorted(Comparator.comparing(p -> p.getMetadata().getName()))
                .toList();
        Pod target;
        if (containerLogsDto.getReplicaIndex() != null) {
            int index = Math.min(Math.max(containerLogsDto.getReplicaIndex(), 0), sorted.size() - 1);
            target = sorted.get(index);
        } else {
            target = sorted.stream().filter(this::isReady).findFirst().orElse(sorted.get(0));
        }
        String fullLog = client.pods().inNamespace(namespace).resource(target).getLog();
        int tail = containerLogsDto.getTail() == null
                ? DEFAULT_LOG_TAIL
                : Math.min(Math.max(containerLogsDto.getTail(), 1), MAX_LOG_TAIL);

        ContainerLogVo vo = new ContainerLogVo();
        vo.setContainerId(target.getMetadata().getName());
        vo.setContainerName(target.getMetadata().getName());
        vo.setLog(trimTail(fullLog, tail));
        return vo;
    }

    // ---------- 资源构建 ----------

    private Deployment buildDeployment(String namespace, Integer stackId, ServiceDo service, List<VolumeDo> volumes) {
        Map<String, String> labels = deploymentLabels(stackId, service);
        String deploymentName = resourceName(service.getServiceName());
        List<String> command = commandTokens(service);
        int replicas = service.getReplicas() == null ? 1 : Math.max(1, service.getReplicas());

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName(deploymentName)
                .withImage(service.getImage())
                .withEnv(buildEnvVars(service))
                .withPorts(buildContainerPorts(service.getContainerPorts()))
                .withResources(buildResources(service))
                .withVolumeMounts(buildVolumeMounts(volumes))
                .withReadinessProbe(buildProbe(service))
                .withLivenessProbe(buildProbe(service));
        if (!CollectionUtils.isEmpty(command)) {
            // 与 Docker withCmd 语义对齐：覆盖镜像 CMD（保留 ENTRYPOINT）
            containerBuilder.withArgs(command);
        }
        Container container = containerBuilder.build();

        return new DeploymentBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(deploymentName)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withSelector(new LabelSelectorBuilder().withMatchLabels(labels).build())
                .withStrategy(buildStrategy(service))
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata().withLabels(labels).endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withContainers(container)
                                .withVolumes(buildVolumes(volumes))
                                .build())
                        .build())
                .endSpec()
                .build();
    }

    private void createDeployment(KubernetesClient client, String namespace, Deployment deployment) {
        client.apps().deployments().inNamespace(namespace).resource(deployment).createOrReplace();
    }

    /**
 * 端口即 SVC：为服务的每个端口创建一个 K8s Service（名 `{服务名}-{端口}`），
 * SVC 类型取端口上的 serviceType（空 = 自动：≤2767 固定 NodePort 30000+端口，否则 ClusterIP）。
 * NodePort 槽位在创建后不可直接替换，故已存在且 spec 一致时跳过，仅 spec 变化才删旧建新；
 * 结束后清理该服务不再存在的旧端口 SVC。
 */
private List<io.fabric8.kubernetes.api.model.Service> buildServices(String namespace, Integer stackId,
                                                              ServiceDo service, List<PortDo> ports) {
    if (CollectionUtils.isEmpty(ports)) {
        return Collections.emptyList();
    }
    Map<String, String> labels = deploymentLabels(stackId, service);
    List<io.fabric8.kubernetes.api.model.Service> result = new ArrayList<>();
    for (PortDo port : ports) {
        result.add(new ServiceBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(svcName(service.getServiceName(), port))
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withType(resolveSvcType(port.getServiceType(), port.getPort()))
                .withSelector(labels)
                .withPorts(List.of(buildServicePort(port)))
                .endSpec()
                .build());
    }
    return result;
}

/**
 * 应用/幂等维护 SVC：NodePort 槽位创建后不可直接替换，已存在且 spec 一致则跳过；
 * 结束后清理该服务下不再声明的旧端口 SVC（端口变更后重部署）
 */
private void applyServices(KubernetesClient client, String namespace, Integer stackId, ServiceDo service,
                           List<io.fabric8.kubernetes.api.model.Service> svcs) {
    Set<String> expectedNames = new HashSet<>();
    for (io.fabric8.kubernetes.api.model.Service svc : svcs) {
        String name = svc.getMetadata().getName();
        expectedNames.add(name);
        io.fabric8.kubernetes.api.model.Service existing =
                client.services().inNamespace(namespace).withName(name).get();
        if (existing != null) {
            if (!sameServiceSpec(existing, svc)) {
                log.info("service spec changed, recreate k8s svc, name={}", name);
                client.services().inNamespace(namespace).withName(name).delete();
                client.services().inNamespace(namespace).resource(svc).create();
            }
            continue;
        }
        client.services().inNamespace(namespace).resource(svc).create();
    }
    for (io.fabric8.kubernetes.api.model.Service svc : client.services().inNamespace(namespace)
            .withLabel(Constants.SERVICE_ID_LABEL, String.valueOf(service.getId())).list().getItems()) {
        if (!expectedNames.contains(svc.getMetadata().getName())) {
            client.services().inNamespace(namespace).withName(svc.getMetadata().getName()).delete();
        }
    }
}

/**
 * 每个声明的端口一个 ServicePort：自动/NodePort 且 ≤2767 固定映射 30000+端口，超出交给 k8s 自动分配
 */
private ServicePort buildServicePort(PortDo port) {
    int declared = port.getPort();
    ServicePortBuilder builder = new ServicePortBuilder()
            .withPort(declared)
            .withTargetPort(new IntOrString(declared))
            .withProtocol(port.getProtocol() == null ? "TCP" : port.getProtocol().toUpperCase());
    String type = resolveSvcType(port.getServiceType(), declared);
    if (("NodePort".equals(type) || type == null) && declared <= NODE_PORT_MAX_DECLARED) {
        builder.withNodePort(NODE_PORT_BASE + declared);
    }
    return builder.build();
}

/**
 * 解析端口 SVC 类型：显式 serviceType（ClusterIP/NodePort/LoadBalancer）；
 * 空 = 自动（≤2767 端口 → NodePort，其余 ClusterIP）
 */
private String resolveSvcType(String serviceType, int declaredPort) {
    String normalized = normalizeServiceType(serviceType);
    if (normalized != null) {
        return normalized;
    }
    return declaredPort <= NODE_PORT_MAX_DECLARED ? "NodePort" : "ClusterIP";
}

/**
 * 端口对应 SVC 名称：{服务名}-{端口}（栈内服务名唯一，DNS-1035 字母开头）
 */
private String svcName(String serviceName, PortDo port) {
    return sanitize(serviceName + "-" + port.getPort());
}

    /**
     * Service spec 一致性比较：type + 端口（协议/端口/NodePort/目标端口）都一致视为幂等
     */
    private boolean sameServiceSpec(io.fabric8.kubernetes.api.model.Service a,
                                    io.fabric8.kubernetes.api.model.Service b) {
        if (!Objects.equals(a.getSpec().getType(), b.getSpec().getType())) {
            return false;
        }
        List<ServicePort> pa = a.getSpec().getPorts() == null ? Collections.emptyList() : a.getSpec().getPorts();
        List<ServicePort> pb = b.getSpec().getPorts() == null ? Collections.emptyList() : b.getSpec().getPorts();
        if (pa.size() != pb.size()) {
            return false;
        }
        for (ServicePort candidate : pb) {
            boolean matched = pa.stream().anyMatch(p -> sameServicePort(p, candidate));
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private boolean sameServicePort(ServicePort a, ServicePort b) {
        // 任一侧未指定 NodePort（交给 k8s 自动分配）时不参与比较，避免每次重部署误判 spec 变化
        boolean nodePortSame = a.getNodePort() == null || b.getNodePort() == null
                || Objects.equals(a.getNodePort(), b.getNodePort());
        return Objects.equals(a.getPort(), b.getPort())
                && Objects.equals(a.getProtocol(), b.getProtocol())
                && nodePortSame
                && Objects.equals(targetPortValue(a.getTargetPort()), targetPortValue(b.getTargetPort()));
    }

    /**
     * 归一化 K8s Service 类型：ClusterIP / NodePort / LoadBalancer；无法识别返回空（走自动）
     */
    private String normalizeServiceType(String serviceType) {
        if (!StringUtils.hasText(serviceType)) {
            return null;
        }
        return switch (serviceType.trim().toLowerCase()) {
            case "clusterip" -> "ClusterIP";
            case "nodeport" -> "NodePort";
            case "loadbalancer" -> "LoadBalancer";
            default -> null;
        };
    }

    private Integer targetPortValue(IntOrString targetPort) {
        if (targetPort == null) {
            return null;
        }
        return targetPort.getIntVal() != null ? targetPort.getIntVal()
                : (targetPort.getStrVal() != null ? Integer.valueOf(targetPort.getStrVal()) : null);
    }

    /**
     * 栈命名空间不存在时自动创建（无权限则忽略，需预先准备）
     */
    private void ensureNamespace(KubernetesClient client, StackDo stack) {
        String namespace = namespaceOf(stack);
        if (client.namespaces().withName(namespace).get() != null) {
            return;
        }
        try {
            client.namespaces().resource(new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(namespace)
                    .addToLabels(Constants.STACK_ID_LABEL, String.valueOf(stack.getId()))
                    .endMetadata().build()).create();
        } catch (Exception e) {
            log.warn("create namespace err (may lack permission), ns={}", namespace, e);
        }
    }

    /**
     * 确保服务的 PVC 存在（幂等）。下架时不删除，与 Docker named volume 语义对齐
     */
    private List<PersistentVolumeClaim> buildPvcs(String namespace, Integer stackId, ServiceDo service,
                                                  List<VolumeDo> volumes) {
        if (CollectionUtils.isEmpty(volumes)) {
            return Collections.emptyList();
        }
        List<PersistentVolumeClaim> result = new ArrayList<>();
        for (VolumeDo volume : volumes) {
            result.add(new PersistentVolumeClaimBuilder()
                    .withNewMetadata()
                    .withNamespace(namespace)
                    .withName(pvcName(volume))
                    .withLabels(deploymentLabels(stackId, service))
                    .endMetadata()
                    .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withNewResources()
                    .addToRequests("storage", new Quantity(volume.getSize() + "Gi"))
                    .endResources()
                    .endSpec()
                    .build());
        }
        return result;
    }

    private void applyPvcs(KubernetesClient client, String namespace, List<PersistentVolumeClaim> pvcs) {
        for (PersistentVolumeClaim pvc : pvcs) {
            String name = pvc.getMetadata().getName();
            if (client.persistentVolumeClaims().inNamespace(namespace).withName(name).get() != null) {
                continue;
            }
            try {
                client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();
            } catch (Exception e) {
                log.warn("create pvc err, name={}", name, e);
            }
        }
    }

    private void removeOrphanDeployments(KubernetesClient client, String namespace, Integer stackId,
                                         List<ServiceDo> services) {
        Set<String> serviceIds = services.stream().map(s -> String.valueOf(s.getId())).collect(Collectors.toSet());
        for (Deployment deployment : listStackDeployments(client, namespace, stackId)) {
            String serviceId = labelOf(deployment, Constants.SERVICE_ID_LABEL);
            if (serviceId == null || !serviceIds.contains(serviceId)) {
                client.apps().deployments().inNamespace(namespace)
                        .withName(deployment.getMetadata().getName()).delete();
            }
        }
    }

    private void removeOrphanServices(KubernetesClient client, String namespace, Integer stackId,
                                      List<ServiceDo> services) {
        Set<String> serviceIds = services.stream().map(s -> String.valueOf(s.getId())).collect(Collectors.toSet());
        for (io.fabric8.kubernetes.api.model.Service svc : client.services().inNamespace(namespace)
                .withLabel(Constants.STACK_ID_LABEL, String.valueOf(stackId)).list().getItems()) {
            String serviceId = svc.getMetadata() != null && svc.getMetadata().getLabels() != null
                    ? svc.getMetadata().getLabels().get(Constants.SERVICE_ID_LABEL) : null;
            if (serviceId == null || !serviceIds.contains(serviceId)) {
                client.services().inNamespace(namespace).withName(svc.getMetadata().getName()).delete();
            }
        }
    }

    private List<Deployment> listStackDeployments(KubernetesClient client, String namespace, Integer stackId) {
        return client.apps().deployments().inNamespace(namespace)
                .withLabel(Constants.STACK_ID_LABEL, String.valueOf(stackId))
                .list().getItems();
    }

    private String labelOf(HasMetadata resource, String key) {
        if (resource == null || resource.getMetadata() == null || resource.getMetadata().getLabels() == null) {
            return null;
        }
        return resource.getMetadata().getLabels().get(key);
    }

    /**
     * 拉取栈下对应 Service 资源（无则空列表）
     */
    private List<io.fabric8.kubernetes.api.model.Service> listStackServices(KubernetesClient client,
                                                                            String namespace, Integer stackId) {
        try {
            var op = client.services();
            if (op == null) {
                return Collections.emptyList();
            }
            return op.inNamespace(namespace)
                    .withLabel(Constants.STACK_ID_LABEL, String.valueOf(stackId))
                    .list().getItems();
        } catch (Exception e) {
            log.warn("list stack services err, ns={}, stackId={}", namespace, stackId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 取首个节点 InternalIP（用于 NodePort 展示；无节点信息返回空）
     */
    private String firstNodeInternalIp(KubernetesClient client) {
        try {
            var nodesOp = client.nodes();
            if (nodesOp == null) {
                return null;
            }
            NodeList nodes = nodesOp.list();
            if (nodes == null || nodes.getItems() == null) {
                return null;
            }
            for (Node node : nodes.getItems()) {
                if (node.getStatus() == null || node.getStatus().getAddresses() == null) {
                    continue;
                }
                for (NodeAddress address : node.getStatus().getAddresses()) {
                    if ("InternalIP".equals(address.getType()) && StringUtils.hasText(address.getAddress())) {
                        return address.getAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("list nodes for exposed addr err", e);
        }
        return null;
    }

    /**
     * 按 Service 内容生成暴露地址：
     * LoadBalancer → externalIP:port（未就绪显示 LB pending）；NodePort → nodeIp:nodePort；
     * ClusterIP → clusterIp:port（集群内访问）。
     */
    List<String> buildK8sExposedAddresses(io.fabric8.kubernetes.api.model.Service svc, String nodeIp) {
        if (svc == null || svc.getSpec() == null || svc.getSpec().getPorts() == null) {
            return Collections.emptyList();
        }
        String type = svc.getSpec().getType();
        String clusterIp = svc.getSpec().getClusterIP();
        List<String> result = new ArrayList<>();
        if ("LoadBalancer".equals(type)) {
            List<String> ingress = new ArrayList<>();
            if (svc.getStatus() != null && svc.getStatus().getLoadBalancer() != null
                    && svc.getStatus().getLoadBalancer().getIngress() != null) {
                for (LoadBalancerIngress entry : svc.getStatus().getLoadBalancer().getIngress()) {
                    String ip = entry.getIp() != null ? entry.getIp() : entry.getHostname();
                    if (StringUtils.hasText(ip)) {
                        ingress.add(ip.trim());
                    }
                }
            }
            for (ServicePort port : svc.getSpec().getPorts()) {
                if (ingress.isEmpty()) {
                    result.add("LB pending:" + port.getPort());
                } else {
                    ingress.forEach(ip -> result.add(ip + ":" + port.getPort()));
                }
            }
        } else if ("NodePort".equals(type)) {
            String host = StringUtils.hasText(nodeIp) ? nodeIp
                    : (StringUtils.hasText(clusterIp) ? clusterIp : "NodePort");
            for (ServicePort port : svc.getSpec().getPorts()) {
                if (port.getNodePort() != null) {
                    result.add(host + ":" + port.getNodePort());
                }
            }
        } else {
            for (ServicePort port : svc.getSpec().getPorts()) {
                result.add((StringUtils.hasText(clusterIp) ? clusterIp : "ClusterIP") + ":" + port.getPort());
            }
        }
        return result;
    }

    private DeploymentStrategy buildStrategy(ServiceDo service) {
        String type = "RollingUpdate".equalsIgnoreCase(service.getStrategy()) ? "RollingUpdate" : "Recreate";
        return new DeploymentStrategyBuilder().withType(type).build();
    }

    /**
     * 健康检查 → readiness / liveness exec 探针（与 Docker HEALTHCHECK 参数对齐）
     */
    private Probe buildProbe(ServiceDo service) {
        if (!StringUtils.hasText(service.getHealthCheckCmd())) {
            return null;
        }
        return new ProbeBuilder()
                .withNewExec()
                .withCommand(List.of("sh", "-c", service.getHealthCheckCmd().trim()))
                .endExec()
                .withInitialDelaySeconds(HEALTHCHECK_START_PERIOD_SECONDS)
                .withPeriodSeconds(HEALTHCHECK_INTERVAL_SECONDS)
                .withTimeoutSeconds(HEALTHCHECK_TIMEOUT_SECONDS)
                .withFailureThreshold(HEALTHCHECK_RETRIES)
                .build();
    }

    private List<EnvVar> buildEnvVars(ServiceDo service) {
        if (CollectionUtils.isEmpty(service.getEnvs())) {
            return Collections.emptyList();
        }
        return service.getEnvs().stream()
                .filter(e -> StringUtils.hasText(e.getName()))
                .map(e -> new EnvVarBuilder().withName(e.getName()).withValue(e.getValue()).build())
                .toList();
    }

    private List<ContainerPort> buildContainerPorts(List<com.github.zer0e.vanilla.domain.ContainerPort> containerPorts) {
        if (CollectionUtils.isEmpty(containerPorts)) {
            return Collections.emptyList();
        }
        return containerPorts.stream()
                .map(p -> new ContainerPortBuilder()
                        .withContainerPort(p.getPort())
                        .withProtocol(p.getProtocol() == null ? "TCP" : p.getProtocol().toUpperCase())
                        .build())
                .toList();
    }

    /**
     * CPU shares → 以 m（千分之一核）近似映射，1024 = 1 vCPU
     */
    private ResourceRequirements buildResources(ServiceDo service) {
        Map<String, Quantity> requests = new HashMap<>();
        Map<String, Quantity> limits = new HashMap<>();
        if (service.getCpu() != null) {
            requests.put("cpu", new Quantity(service.getCpu() + "m"));
            limits.put("cpu", new Quantity(service.getCpu() + "m"));
        }
        if (service.getMemory() != null) {
            requests.put("memory", new Quantity(service.getMemory() + "Mi"));
            limits.put("memory", new Quantity(service.getMemory() + "Mi"));
        }
        if (requests.isEmpty()) {
            return null;
        }
        return new ResourceRequirementsBuilder().withRequests(requests).withLimits(limits).build();
    }

    private List<Volume> buildVolumes(List<VolumeDo> volumes) {
        if (CollectionUtils.isEmpty(volumes)) {
            return Collections.emptyList();
        }
        return volumes.stream().map(v -> {
            String name = pvcName(v);
            return new VolumeBuilder()
                    .withName(name)
                    .withNewPersistentVolumeClaim().withClaimName(name).endPersistentVolumeClaim()
                    .build();
        }).toList();
    }

    private List<VolumeMount> buildVolumeMounts(List<VolumeDo> volumes) {
        if (CollectionUtils.isEmpty(volumes)) {
            return Collections.emptyList();
        }
        return volumes.stream()
                .map(v -> new VolumeMountBuilder()
                        .withName(pvcName(v))
                        .withMountPath(v.getMountPath())
                        .build())
                .toList();
    }

    private List<String> commandTokens(ServiceDo service) {
        List<String> tokens = new ArrayList<>();
        if (StringUtils.hasText(service.getCommand())) {
            Collections.addAll(tokens, service.getCommand().trim().split("\\s+"));
        }
        if (StringUtils.hasText(service.getArgs())) {
            Collections.addAll(tokens, service.getArgs().trim().split("\\s+"));
        }
        return tokens;
    }

    private boolean isReady(Pod pod) {
        if (pod.getStatus() == null || CollectionUtils.isEmpty(pod.getStatus().getContainerStatuses())) {
            return false;
        }
        return pod.getStatus().getContainerStatuses().stream()
                .allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
    }

    private String trimTail(String log, int tail) {
        if (log == null) {
            return "";
        }
        String[] lines = log.split("\n", -1);
        if (lines.length <= tail) {
            return log;
        }
        return String.join("\n", Arrays.copyOfRange(lines, lines.length - tail, lines.length));
    }

    private Map<String, String> deploymentLabels(Integer stackId, ServiceDo service) {
        Map<String, String> labels = new HashMap<>();
        labels.put(Constants.STACK_ID_LABEL, String.valueOf(stackId));
        labels.put(Constants.SERVICE_ID_LABEL, String.valueOf(service.getId()));
        return labels;
    }

    /**
     * K8s 资源名 = 服务名（不含任何前缀）：一个栈一个命名空间，服务名在栈内唯一，
     * 命名空间隔离保证跨栈不冲突（Service 名称还须满足 DNS-1035 字母开头，由 sanitize 兜底）
     */
    private String resourceName(String serviceName) {
        return sanitize(serviceName);
    }

    /**
     * PVC 名 = 卷名（卷为栈级独立资源，栈内卷名唯一，命名空间=栈名故不冲突、可被多个服务共享挂载）
     */
    private String pvcName(VolumeDo volume) {
        return sanitize(volume.getVolumeName());
    }

    /**
     * 栈命名空间名 = 栈名清洗后的 DNS-1123 label（小写字母/数字/连字符，≤63 字符；
     * 栈名在同集群唯一 → 命名空间在该集群唯一）
     */
    private String namespaceOf(StackDo stack) {
        String name = stack.getStackName() == null ? "" : stack.getStackName().toLowerCase();
        name = name.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        if (name.length() > 63) {
            name = name.substring(0, 63).replaceAll("-+$", "");
        }
        if (!StringUtils.hasText(name)) {
            name = "stack-" + stack.getId();
        }
        return name;
    }

    private String sanitize(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
    }

    private StackDo getStack(Integer stackId) throws BusinessException {
        StackDo stack = stackMapper.selectById(stackId);
        if (stack == null || stack.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        return stack;
    }

    private ClusterDo getCluster(Integer clusterId) throws BusinessException {
        ClusterDo cluster = clusterMapper.selectById(clusterId);
        if (cluster == null || cluster.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.CLUSTER_NOT_EXIST);
        }
        return cluster;
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public DeployPreviewVo preview(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        ClusterDo cluster = getCluster(stack.getClusterId());
        if (!ClusterType.K8S.name().equalsIgnoreCase(cluster.getType())) {
            return DeployPreviewVo.unsupported();
        }
        String namespace = namespaceOf(stack);
        List<String> manifests = new ArrayList<>();
        manifests.add(Serialization.asYaml(new NamespaceBuilder()
                .withNewMetadata()
                .withName(namespace)
                .addToLabels(Constants.STACK_ID_LABEL, String.valueOf(stack.getId()))
                .endMetadata()
                .build()));
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);
        for (ServiceDo service : services) {
            List<PortDo> ports = portMapper.selectPortsByServiceId(service.getId());
            List<VolumeDo> volumes = volumeMapper.selectVolumesByServiceIds(List.of(service.getId()));
            for (io.fabric8.kubernetes.api.model.Service svc : buildServices(namespace, stack.getId(), service, ports)) {
                manifests.add(Serialization.asYaml(svc));
            }
            manifests.add(Serialization.asYaml(buildDeployment(namespace, stack.getId(), service, volumes)));
            for (PersistentVolumeClaim pvc : buildPvcs(namespace, stack.getId(), service, volumes)) {
                manifests.add(Serialization.asYaml(pvc));
            }
        }
        return DeployPreviewVo.of(String.join("\n---\n", manifests));
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}