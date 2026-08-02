package com.github.zer0e.vanilla.application.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.zer0e.vanilla.application.DeployService;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.KubernetesStackService;
import com.github.zer0e.vanilla.application.support.RuntimeStateResolver;
import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
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
import com.github.zer0e.vanilla.infrastructure.docker.DockerClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    private final DockerClientFactory dockerClientFactory;
    private final StackMapper stackMapper;
    private final ClusterMapper clusterMapper;
    private final ServiceMapper serviceMapper;
    private final PortMapper portMapper;
    private final VolumeMapper volumeMapper;
    private final HistoryService historyService;
    private final KubernetesStackService kubernetesStackService;

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException {
        if (isKubernetes(deployStackDto.getStackId())) {
            return kubernetesStackService.deployStack(deployStackDto);
        }
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);
        validateHostPorts(stack.getId(), services);
        try {
            for (ServiceDo service : services) {
                deployService(client, stack, service);
            }
            // 清理已不存在的服务残留容器（如服务被删除后重新部署）
            removeOrphanContainers(client, stack.getId(), services);
        } catch (BusinessException e) {
            // 部署失败时清理已创建容器，避免残留部分状态
            removeStackContainers(client, stack.getId());
            throw e;
        }
        recordHistory(stack.getId(), "部署栈 " + stack.getStackName());
        return getStackStatus(deployStackDto);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin'," +
            "'stack_' + #deployStackDto.stackId + '_stack_member'," +
            "'stack_' + #deployStackDto.stackId + '_stack_readonly')")
    public StackStatusVo getStackStatus(DeployStackDto deployStackDto) throws BusinessException {
        if (isKubernetes(deployStackDto.getStackId())) {
            return kubernetesStackService.getStackStatus(deployStackDto);
        }
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        List<Container> containers = listStackContainers(client, stack.getId());
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);

        Map<String, List<Container>> byService = containers.stream()
                .filter(c -> c.getLabels() != null && c.getLabels().get(Constants.SERVICE_ID_LABEL) != null)
                .collect(Collectors.groupingBy(c -> c.getLabels().get(Constants.SERVICE_ID_LABEL)));

        List<ServiceStatusVo> serviceStatuses = new ArrayList<>();
        for (ServiceDo service : services) {
            List<Container> svcContainers = byService.getOrDefault(String.valueOf(service.getId()), Collections.emptyList());
            long running = svcContainers.stream().filter(c -> "running".equalsIgnoreCase(c.getState())).count();
            ServiceStatusVo status = new ServiceStatusVo();
            status.setServiceId(service.getId());
            status.setServiceName(service.getServiceName());
            status.setReplicas(service.getReplicas() == null ? 1 : service.getReplicas());
            status.setRunningCount((int) running);
            // 配置了健康检查时逐个 inspect 统计健康数，否则运行即可视为健康
            status.setHealthyCount(hasHealthCheck(service)
                    ? countHealthy(client, svcContainers)
                    : (int) running);
            status.setStatus(resolveStatus(svcContainers.size(), running));
            serviceStatuses.add(status);
        }

        StackStatusVo result = new StackStatusVo();
        result.setStackId(stack.getId());
        result.setStatus(resolveStackStatus(serviceStatuses));
        result.setServices(serviceStatuses);
        return result;
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public void stopStack(DeployStackDto deployStackDto) throws BusinessException {
        if (isKubernetes(deployStackDto.getStackId())) {
            kubernetesStackService.stopStack(deployStackDto);
            return;
        }
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        List<Container> containers = listStackContainers(client, stack.getId());
        for (Container container : containers) {
            try {
                client.stopContainerCmd(container.getId()).exec();
            } catch (Exception e) {
                log.warn("stop container err, containerId={}", container.getId(), e);
            }
        }
        recordHistory(stack.getId(), "停止栈 " + stack.getStackName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public void removeStack(DeployStackDto deployStackDto) throws BusinessException {
        if (isKubernetes(deployStackDto.getStackId())) {
            kubernetesStackService.removeStack(deployStackDto);
            return;
        }
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        removeStackContainers(client, stack.getId());
        recordHistory(stack.getId(), "下架栈 " + stack.getStackName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #containerLogsDto.stackId + '_stack_admin'," +
            "'stack_' + #containerLogsDto.stackId + '_stack_member'," +
            "'stack_' + #containerLogsDto.stackId + '_stack_readonly')")
    public ContainerLogVo getContainerLog(ContainerLogsDto containerLogsDto) throws BusinessException {
        if (isKubernetes(containerLogsDto.getStackId())) {
            return kubernetesStackService.getContainerLog(containerLogsDto);
        }
        StackDo stack = getStack(containerLogsDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        List<Container> containers = listServiceContainers(client, stack.getId(), containerLogsDto.getServiceId());
        if (CollectionUtils.isEmpty(containers)) {
            throw new BusinessException("服务未部署，无可查看日志的容器");
        }
        Container target = selectReplicaContainer(containers, containerLogsDto.getReplicaIndex());
        int tail = containerLogsDto.getTail() == null
                ? DEFAULT_LOG_TAIL
                : Math.min(Math.max(containerLogsDto.getTail(), 1), MAX_LOG_TAIL);

        ContainerLogVo vo = new ContainerLogVo();
        vo.setContainerId(target.getId());
        vo.setContainerName(firstContainerName(target));
        vo.setLog(fetchContainerLogs(client, target.getId(), tail));
        return vo;
    }

    /**
     * 选择一个副本容器：单副本直接返回；指定副本索引时按名字后缀匹配；
     * 否则优先返回运行中的副本（多副本时日志查看默认取第一个运行中的）
     */
    private Container selectReplicaContainer(List<Container> containers, Integer replicaIndex) {
        if (containers.size() == 1) {
            return containers.get(0);
        }
        if (replicaIndex != null) {
            String suffix = "-" + replicaIndex;
            for (Container container : containers) {
                if (firstContainerName(container) != null && firstContainerName(container).endsWith(suffix)) {
                    return container;
                }
            }
        }
        return containers.stream()
                .filter(c -> "running".equalsIgnoreCase(c.getState()))
                .findFirst()
                .orElse(containers.get(0));
    }

    private String firstContainerName(Container container) {
        if (container.getNames() == null || container.getNames().length == 0) {
            return container.getId();
        }
        return stripSlash(container.getNames()[0]);
    }

    /**
     * 读取容器最近日志（stdout + stderr）。docker-java 的 LogContainerResultCallback
     * 会在全部输出落盘后把整段日志拼成一个字符串
     */
    private String fetchContainerLogs(DockerClient client, String containerId, int tail) throws BusinessException {
        try {
            LogContainerResultCallback callback = client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .exec(new LogContainerResultCallback());
            callback.awaitCompletion();
            return callback.toString();
        } catch (Exception e) {
            log.error("read container log err, containerId={}", containerId, e);
            throw new BusinessException("读取容器日志失败：" + containerId);
        }
    }

    /**
     * 校验整个栈的宿主端口分配：每个服务每个副本占用 port + index 宿主端口，
     * 跨服务/副本重复时在创建容器前快速失败，避免部分部署
     */
    void validateHostPorts(Integer stackId, List<ServiceDo> services) throws BusinessException {
        Map<Integer, String> allocated = new HashMap<>();
        for (ServiceDo service : services) {
            List<PortDo> ports = portMapper.selectPortsByServiceId(service.getId());
            int replicas = service.getReplicas() == null ? 1 : Math.max(1, service.getReplicas());
            for (PortDo port : ports) {
                for (int i = 0; i < replicas; i++) {
                    int hostPort = port.getPort() + i;
                    String owner = "服务[" + service.getServiceName() + "] 副本" + i;
                    String exist = allocated.putIfAbsent(hostPort, owner);
                    if (exist != null) {
                        throw new BusinessException("宿主端口 " + hostPort + " 冲突：" + exist
                                + " 与 " + owner + " 重复，请调整服务端口配置");
                    }
                }
            }
        }
    }

    /**
     * 部署单个服务：拉取镜像后按更新策略创建/替换容器
     */
    private void deployService(DockerClient client, StackDo stack, ServiceDo service) throws BusinessException {
        try {
            pullImage(client, service.getImage());
            List<PortDo> ports = portMapper.selectPortsByServiceId(service.getId());
            List<VolumeDo> volumes = volumeMapper.selectVolumesByServiceIdAndSearch(service.getId(), null);
            ensureDockerVolumes(client, stack.getId(), service.getId(), volumes);
            int replicas = service.getReplicas() == null ? 1 : Math.max(1, service.getReplicas());
            List<Container> existing = listServiceContainers(client, stack.getId(), service.getId());
            if (isRollingUpdate(service)) {
                rollingUpdateService(client, stack, service, ports, volumes, replicas, existing);
            } else {
                recreateService(client, stack, service, ports, volumes, replicas, existing);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("deploy service err, stackId={}, service={}", stack.getId(), service.getServiceName(), e);
            throw new BusinessException(Constants.DEPLOY_FAIL + "：" + service.getServiceName() + " " + e.getMessage());
        }
    }

    /**
     * 确保服务的 Docker named volume 存在（部署前幂等创建）
     */
    private void ensureDockerVolumes(DockerClient client, Integer stackId, Integer serviceId, List<VolumeDo> volumes) {
        if (CollectionUtils.isEmpty(volumes)) {
            return;
        }
        for (VolumeDo volume : volumes) {
            String name = dockerVolumeName(stackId, serviceId, volume);
            boolean exists;
            try {
                client.inspectVolumeCmd(name).exec();
                exists = true;
            } catch (NotFoundException e) {
                exists = false;
            }
            if (!exists) {
                try {
                    client.createVolumeCmd().withName(name)
                            .withDriverOpts(Map.of("size", String.valueOf(volume.getSize())))
                            .exec();
                } catch (Exception e) {
                    log.warn("create volume with size opt err, name={}, retry without opts", name, e);
                    client.createVolumeCmd().withName(name).exec();
                }
            }
        }
    }

    /**
     * 构建卷挂载绑定（Docker named volume → 容器挂载路径）
     */
    List<Bind> buildVolumeBinds(Integer stackId, Integer serviceId, List<VolumeDo> volumes) {
        if (CollectionUtils.isEmpty(volumes)) {
            return Collections.emptyList();
        }
        return volumes.stream()
                .map(v -> new Bind(dockerVolumeName(stackId, serviceId, v), new Volume(v.getMountPath())))
                .toList();
    }

    private String dockerVolumeName(Integer stackId, Integer serviceId, VolumeDo volume) {
        return sanitizeContainerName(Constants.CONTAINER_NAME_PREFIX + stackId + "-" + serviceId
                + "-" + volume.getVolumeName());
    }

    private boolean isRollingUpdate(ServiceDo service) {
        return "RollingUpdate".equalsIgnoreCase(service.getStrategy());
    }

    /**
     * Recreate（默认）：先删该服务旧容器，再按副本数全量创建
     */
    void recreateService(DockerClient client, StackDo stack, ServiceDo service, List<PortDo> ports,
                         List<VolumeDo> volumes, int replicas, List<Container> existing) {
        for (Container container : existing) {
            removeContainer(client, container.getId());
        }
        for (int i = 0; i < replicas; i++) {
            startReplica(client, stack, service, ports, volumes, i, replicas);
        }
    }

    /**
     * RollingUpdate：逐副本「停旧 → 建新」，其余副本持续对外服务。
     * 副本数变化（扩容/缩容）时命名与宿主端口会错位，退化为全量重建
     */
    void rollingUpdateService(DockerClient client, StackDo stack, ServiceDo service, List<PortDo> ports,
                              List<VolumeDo> volumes, int replicas, List<Container> existing) {
        if (existing.size() != replicas) {
            recreateService(client, stack, service, ports, volumes, replicas, existing);
            return;
        }
        for (int i = 0; i < replicas; i++) {
            Integer index = replicas > 1 ? i : null;
            String name = containerName(stack.getId(), service.getServiceName(), index);
            Container old = findByContainerName(existing, name);
            if (old != null) {
                removeContainer(client, old.getId());
            }
            startReplica(client, stack, service, ports, volumes, i, replicas);
        }
    }

    private void startReplica(DockerClient client, StackDo stack, ServiceDo service, List<PortDo> ports,
                              List<VolumeDo> volumes, int i, int replicas) {
        Integer index = replicas > 1 ? i : null;
        String name = containerName(stack.getId(), service.getServiceName(), index);
        CreateContainerResponse response = createContainer(client, stack, service, ports, volumes, name, index);
        client.startContainerCmd(response.getId()).exec();
    }

    /**
     * 清理栈下不属于任何现存服务的容器（服务删除后的残留）
     */
    private void removeOrphanContainers(DockerClient client, Integer stackId, List<ServiceDo> services) {
        Set<String> serviceIds = services.stream().map(s -> String.valueOf(s.getId())).collect(Collectors.toSet());
        for (Container container : listStackContainers(client, stackId)) {
            String serviceId = container.getLabels() == null
                    ? null : container.getLabels().get(Constants.SERVICE_ID_LABEL);
            if (serviceId == null || !serviceIds.contains(serviceId)) {
                removeContainer(client, container.getId());
            }
        }
    }

    private List<Container> listServiceContainers(DockerClient client, Integer stackId, Integer serviceId) {
        return client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(
                        Constants.STACK_ID_LABEL, String.valueOf(stackId),
                        Constants.SERVICE_ID_LABEL, String.valueOf(serviceId)))
                .exec();
    }

    private Container findByContainerName(List<Container> containers, String name) {
        for (Container container : containers) {
            if (container.getNames() != null && container.getNames().length > 0
                    && name.equalsIgnoreCase(stripSlash(container.getNames()[0]))) {
                return container;
            }
        }
        return null;
    }

    private String stripSlash(String name) {
        return name != null && name.startsWith("/") ? name.substring(1) : name;
    }

    private void removeContainer(DockerClient client, String containerId) {
        try {
            client.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("remove container err, containerId={}", containerId, e);
        }
    }

    private CreateContainerResponse createContainer(DockerClient client, StackDo stack, ServiceDo service,
                                                    List<PortDo> ports, List<VolumeDo> volumes,
                                                    String containerName, Integer replicaIndex) {
        CreateContainerCmd cmd = client.createContainerCmd(service.getImage())
                .withName(containerName)
                .withLabels(buildLabels(stack.getId(), service));
        List<String> env = buildEnvs(service.getEnvs());
        if (!CollectionUtils.isEmpty(env)) {
            cmd.withEnv(env);
        }
        buildCommand(cmd, service);
        HealthCheck healthCheck = buildHealthCheck(service);
        if (healthCheck != null) {
            cmd.withHealthcheck(healthCheck);
        }

        // 端口映射、卷挂载、资源限制需放入同一个 HostConfig，否则 withHostConfig 会覆盖前面的结果。
        // 多副本时宿主端口按副本索引递增偏移，避免端口冲突
        List<ExposedPort> exposedPorts = new ArrayList<>();
        HostConfig hostConfig = buildHostConfig(service, ports, replicaIndex, exposedPorts);
        List<Bind> binds = buildVolumeBinds(stack.getId(), service.getId(), volumes);
        if (!CollectionUtils.isEmpty(binds)) {
            hostConfig.withBinds(binds);
        }
        if (!exposedPorts.isEmpty()) {
            cmd.withExposedPorts(exposedPorts);
        }
        cmd.withHostConfig(hostConfig);
        return cmd.exec();
    }

    /**
     * 构建容器 HostConfig：端口绑定（宿主端口 = 声明端口 + 副本索引）+ CPU/内存限制。
     * 端口绑定与资源限制放在同一个 HostConfig，避免被 withHostConfig 覆盖
     *
     * @param exposedPorts 出参，收集需要暴露的容器端口
     */
    HostConfig buildHostConfig(ServiceDo service, List<PortDo> ports, Integer replicaIndex, List<ExposedPort> exposedPorts) {
        HostConfig hostConfig = HostConfig.newHostConfig();
        if (!CollectionUtils.isEmpty(ports)) {
            Ports portBindings = new Ports();
            int offset = replicaIndex == null ? 0 : replicaIndex;
            for (PortDo port : ports) {
                ExposedPort exposedPort = "udp".equalsIgnoreCase(port.getProtocol())
                        ? ExposedPort.udp(port.getPort()) : ExposedPort.tcp(port.getPort());
                exposedPorts.add(exposedPort);
                portBindings.bind(exposedPort, Ports.Binding.bindPort(port.getPort() + offset));
            }
            hostConfig.withPortBindings(portBindings);
        }
        if (service.getCpu() != null) {
            hostConfig.withCpuShares(service.getCpu());
        }
        if (service.getMemory() != null) {
            hostConfig.withMemory(service.getMemory() * 1024L * 1024L);
        }
        return hostConfig;
    }

    private void buildCommand(CreateContainerCmd cmd, ServiceDo service) {
        List<String> command = new ArrayList<>();
        if (StringUtils.hasText(service.getCommand())) {
            Collections.addAll(command, service.getCommand().trim().split("\\s+"));
        }
        if (StringUtils.hasText(service.getArgs())) {
            Collections.addAll(command, service.getArgs().trim().split("\\s+"));
        }
        if (!command.isEmpty()) {
            cmd.withCmd(command.toArray(new String[0]));
        }
    }

    private void pullImage(DockerClient client, String image) throws BusinessException {
        try {
            client.pullImageCmd(image).start().awaitCompletion();
        } catch (Exception e) {
            log.error("pull image err, image={}", image, e);
            throw new BusinessException("拉取镜像 " + image + " 失败：" + e.getMessage());
        }
    }

    private List<String> buildEnvs(List<Env> envs) {
        if (CollectionUtils.isEmpty(envs)) {
            return null;
        }
        return envs.stream().map(e -> e.getName() + "=" + e.getValue()).toList();
    }

    private boolean hasHealthCheck(ServiceDo service) {
        return service != null && StringUtils.hasText(service.getHealthCheckCmd());
    }

    /**
     * 构建 Docker HEALTHCHECK（CMD-SHELL 形式）。未配置健康检查时返回 null。
     * 参数采用平台默认值：间隔 30s / 超时 10s / 重试 3 次 / 启动宽限 5s
     */
    HealthCheck buildHealthCheck(ServiceDo service) {
        if (!hasHealthCheck(service)) {
            return null;
        }
        return new HealthCheck()
                .withTest(List.of("CMD-SHELL", service.getHealthCheckCmd().trim()))
                // Docker API 以纳秒为单位
                .withInterval(Duration.ofSeconds(HEALTHCHECK_INTERVAL_SECONDS).toNanos())
                .withTimeout(Duration.ofSeconds(HEALTHCHECK_TIMEOUT_SECONDS).toNanos())
                .withRetries(HEALTHCHECK_RETRIES)
                .withStartPeriod(Duration.ofSeconds(HEALTHCHECK_START_PERIOD_SECONDS).toNanos());
    }

    /**
     * 统计运行中且健康的容器数。配置了健康检查的容器只有 docker 标记 healthy 才计健康；
     * 尚未产生健康状态（probe 启动期）或 inspect 异常时按健康处理，避免误报
     */
    int countHealthy(DockerClient client, List<Container> containers) {
        if (CollectionUtils.isEmpty(containers)) {
            return 0;
        }
        int healthy = 0;
        for (Container container : containers) {
            if (!"running".equalsIgnoreCase(container.getState())) {
                continue;
            }
            try {
                ContainerState state = client.inspectContainerCmd(container.getId()).exec().getState();
                HealthState health = state.getHealth();
                if (health == null || "healthy".equalsIgnoreCase(health.getStatus())) {
                    healthy++;
                }
            } catch (Exception e) {
                log.warn("inspect container health err, containerId={}", container.getId(), e);
                healthy++;
            }
        }
        return healthy;
    }

    private Map<String, String> buildLabels(Integer stackId, ServiceDo service) {
        Map<String, String> labels = new HashMap<>();
        labels.put(Constants.STACK_ID_LABEL, String.valueOf(stackId));
        labels.put(Constants.SERVICE_ID_LABEL, String.valueOf(service.getId()));
        return labels;
    }

    String containerName(Integer stackId, String serviceName, Integer index) {
        String name = Constants.CONTAINER_NAME_PREFIX + stackId + "-" + serviceName;
        if (index != null) {
            name += "-" + index;
        }
        return sanitizeContainerName(name);
    }

    private String sanitizeContainerName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_.-]", "-");
    }

    private List<Container> listStackContainers(DockerClient client, Integer stackId) {
        return client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(Constants.STACK_ID_LABEL, String.valueOf(stackId)))
                .exec();
    }

    private void removeStackContainers(DockerClient client, Integer stackId) {
        for (Container container : listStackContainers(client, stackId)) {
            try {
                client.removeContainerCmd(container.getId()).withForce(true).exec();
            } catch (Exception e) {
                log.warn("remove container err, containerId={}", container.getId(), e);
            }
        }
    }

    private static final int DEFAULT_LOG_TAIL = 500;
    private static final int MAX_LOG_TAIL = 10000;

    // Docker HEALTHCHECK 默认参数（秒）
    private static final int HEALTHCHECK_INTERVAL_SECONDS = 30;
    private static final int HEALTHCHECK_TIMEOUT_SECONDS = 10;
    private static final int HEALTHCHECK_RETRIES = 3;
    private static final int HEALTHCHECK_START_PERIOD_SECONDS = 5;

    String resolveStatus(int total, long running) {
        return RuntimeStateResolver.resolveStatus(total, running);
    }

    String resolveStackStatus(List<ServiceStatusVo> services) {
        return RuntimeStateResolver.resolveStackStatus(services);
    }

    private StackDo getStack(Integer stackId) throws BusinessException {
        StackDo stack = stackMapper.selectById(stackId);
        if (stack == null || stack.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        return stack;
    }

    /**
     * 按集群类型分流：K8S 集群委托 KubernetesStackService，其余走 Docker 链路
     */
    private boolean isKubernetes(Integer stackId) throws BusinessException {
        StackDo stack = getStack(stackId);
        ClusterDo cluster = clusterMapper.selectById(stack.getClusterId());
        return cluster != null && ClusterType.K8S.name().equalsIgnoreCase(cluster.getType());
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}
