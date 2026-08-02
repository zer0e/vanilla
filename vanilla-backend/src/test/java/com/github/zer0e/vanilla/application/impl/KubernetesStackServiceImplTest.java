package com.github.zer0e.vanilla.application.impl;

import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.DeployPreviewVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.domain.ContainerPort;
import com.github.zer0e.vanilla.domain.DataStatus;
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
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KubernetesStackServiceImpl 核心行为：K8S 集群识别、资源映射、状态语义、停止/下架/日志
 */
@ExtendWith(MockitoExtension.class)
class KubernetesStackServiceImplTest {

    @Mock
    private KubernetesClientFactory kubernetesClientFactory;
    @Mock
    private StackMapper stackMapper;
    @Mock
    private ClusterMapper clusterMapper;
    @Mock
    private ServiceMapper serviceMapper;
    @Mock
    private PortMapper portMapper;
    @Mock
    private VolumeMapper volumeMapper;
    @Mock
    private HistoryService historyService;
    @Mock
    private KubernetesClient client;

    private KubernetesStackServiceImpl kubeStackService;

    @BeforeEach
    void setUp() {
        kubeStackService = new KubernetesStackServiceImpl(
                kubernetesClientFactory, stackMapper, clusterMapper, serviceMapper,
                portMapper, volumeMapper, historyService);
    }

    // ---- K8S 集群识别 ----

    @Test
    void isKubernetes_trueWhenClusterTypeIsK8S() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(ClusterDo.builder().id(1).type("K8S").build());

        assertThat(kubeStackService.isKubernetes(1)).isTrue();
    }

    @Test
    void isKubernetes_falseForDockerCluster() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(ClusterDo.builder().id(1).type("DOCKER").build());

        assertThat(kubeStackService.isKubernetes(1)).isFalse();
    }

    @Test
    void preview_generatesYamlForK8sStack() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(ServiceDo.builder().id(1).stackId(1).serviceName("nginx")
                        .replicas(1).image("nginx:latest")
                        .containerPorts(List.of(new ContainerPort("tcp", 80))).build()));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        DeployPreviewVo vo = kubeStackService.preview(new DeployStackDto(1));

        assertThat(vo.getSupported()).isTrue();
        // fabric8 asYaml 输出值带引号，且每个文档自带 --- 分隔
        assertThat(vo.getYaml())
                .contains("Namespace")
                .contains("Deployment")
                .contains("kind: \"Deployment\"")
                .contains("nginx")
                .contains("nginx-80")
                .contains("Service");
    }

    @Test
    void preview_dockerStack_returnsUnsupported() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(ClusterDo.builder().id(1).type("DOCKER")
                .status(DataStatus.EXIST.ordinal()).build());

        assertThat(kubeStackService.preview(new DeployStackDto(1)).getSupported()).isFalse();
    }

    // ---- 部署：Deployment / Service / PVC 资源映射 ----

    @Test
    void deployStack_createsDeploymentWithCorrectMapping() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(ServiceDo.builder().id(1).stackId(1).serviceName("nginx")
                        .replicas(2).image("nginx:latest")
                        .containerPorts(List.of(new com.github.zer0e.vanilla.domain.ContainerPort("tcp", 80)))
                        .build()));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        stubNamespace();
        Deps deps = stubDeployments();
        stubPvc();
        stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deps.nsOp, atLeastOnce()).resource(captor.capture());
        Deployment deployment = captor.getValue();
        assertThat(deployment.getMetadata().getName()).isEqualTo("nginx");
        assertThat(deployment.getMetadata().getLabels().get(Constants.STACK_ID_LABEL)).isEqualTo("1");
        assertThat(deployment.getMetadata().getLabels().get(Constants.SERVICE_ID_LABEL)).isEqualTo("1");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(2);
        assertThat(deployment.getSpec().getStrategy().getType()).isEqualTo("Recreate");
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage())
                .isEqualTo("nginx:latest");
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().get(0)
                .getContainerPort()).isEqualTo(80);
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs())
                .isNullOrEmpty();
        verify(historyService).createHistory(any());
    }

    @Test
    void deployStack_attachesVolumeMountsWhenVolumesConfigured() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(Collections.emptyList());
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1)))
                .thenReturn(List.of(VolumeDo.builder().stackId(1).serviceId(1)
                        .volumeName("data").size(5).mountPath("/data").build()));

        stubNamespace();
        Deps deps = stubDeployments();
        stubPvc();
        stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deps.nsOp, atLeastOnce()).resource(captor.capture());
        Deployment deployment = captor.getValue();
        assertThat(deployment.getSpec().getTemplate().getSpec().getVolumes().get(0).getName())
                .isEqualTo("data");
        assertThat(deployment.getSpec().getTemplate().getSpec().getVolumes().get(0)
                .getPersistentVolumeClaim().getClaimName()).isEqualTo("data");
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getVolumeMounts().get(0).getMountPath()).isEqualTo("/data");
    }

    // ---- 暴露地址：NodePort / LoadBalancer / ClusterIP ----

    @Test
    void getStackStatus_exposesNodePortAndLoadBalancerAddresses() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(2, "api", 1), service(1, "nginx", 1)));
        stubDeploymentsList(deployment(1, "nginx", "1", 1, 1),
                deployment(2, "api", "2", 1, 1));

        io.fabric8.kubernetes.api.model.Service nodePortSvc = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("nginx")
                .addToLabels(Constants.STACK_ID_LABEL, "1").addToLabels(Constants.SERVICE_ID_LABEL, "1")
                .endMetadata()
                .withNewSpec().withType("NodePort").withClusterIP("192.168.0.1")
                .addNewPort().withPort(80).withProtocol("TCP").withNodePort(30080).endPort()
                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.Service lbSvc = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("api")
                .addToLabels(Constants.STACK_ID_LABEL, "1").addToLabels(Constants.SERVICE_ID_LABEL, "2")
                .endMetadata()
                .withNewSpec().withType("LoadBalancer").withClusterIP("10.0.0.5")
                .addNewPort().withPort(80).withProtocol("TCP").endPort()
                .endSpec()
                .withStatus(new io.fabric8.kubernetes.api.model.ServiceStatusBuilder()
                        .withNewLoadBalancer()
                        .addNewIngress().withIp("1.2.3.4").endIngress()
                        .endLoadBalancer()
                        .build())
                .build();
        stubServicesList(List.of(nodePortSvc, lbSvc));

        var vo = kubeStackService.getStackStatus(new DeployStackDto(1));

        // 无节点 IP 时 NodePort 回退展示 ClusterIP:nodePort；LoadBalancer 展示 externalIP:port
        assertThat(vo.getServices().stream().filter(s -> s.getServiceName().equals("nginx"))
                .findFirst().orElseThrow().getExposedAddresses()).containsExactly("192.168.0.1:30080");
        assertThat(vo.getServices().stream().filter(s -> s.getServiceName().equals("api"))
                .findFirst().orElseThrow().getExposedAddresses()).containsExactly("1.2.3.4:80");
    }

    @Test
    void buildK8sExposedAddresses_loadBalancerPending_showsPending() {
        io.fabric8.kubernetes.api.model.Service svc = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewSpec().withType("LoadBalancer")
                .addNewPort().withPort(80).endPort()
                .endSpec()
                .build();

        assertThat(kubeStackService.buildK8sExposedAddresses(svc, null))
                .containsExactly("LB pending:80");
    }

    // ---- 自定义 Service 类型：ClusterIP / LoadBalancer / 大端口 NodePort ----

    private void stubDeployBase() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(ServiceDo.builder().id(1).stackId(1).serviceName("nginx")
                        .replicas(1).image("nginx:latest").build()));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());
        stubNamespace();
        stubDeployments();
        stubPvc();
    }

    private Service captureCreatedService(Services s) {
        ArgumentCaptor<Service> captor = ArgumentCaptor.forClass(Service.class);
        verify(s.svcNsOp, atLeastOnce()).resource(captor.capture());
        return captor.getValue();
    }

    @Test
    void deployStack_explicitClusterIp_createsClusterIpServiceWithoutNodePort() throws Exception {
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80, "ClusterIP")));
        stubDeployBase();
        Services s = stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        Service svc = captureCreatedService(s);
        // 端口级 SVC：名称 = 服务名-端口
        assertThat(svc.getMetadata().getName()).isEqualTo("nginx-80");
        assertThat(svc.getSpec().getType()).isEqualTo("ClusterIP");
        // 显式 ClusterIP：即使声明端口 80 也不分配 NodePort
        assertThat(svc.getSpec().getPorts().get(0).getNodePort()).isNull();
    }

    @Test
    void deployStack_explicitLoadBalancer_createsLoadBalancerService() throws Exception {
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80, "LoadBalancer")));
        stubDeployBase();
        Services s = stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        Service svc = captureCreatedService(s);
        assertThat(svc.getMetadata().getName()).isEqualTo("nginx-80");
        assertThat(svc.getSpec().getType()).isEqualTo("LoadBalancer");
    }

    @Test
    void deployStack_explicitNodePortOnLargePort_letsKubernetesAllocate() throws Exception {
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 8080, "NodePort")));
        stubDeployBase();
        Services s = stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        Service svc = captureCreatedService(s);
        assertThat(svc.getMetadata().getName()).isEqualTo("nginx-8080");
        assertThat(svc.getSpec().getType()).isEqualTo("NodePort");
        // 8080 > 2767，固定映射放不下 → 不指定 nodePort，交给 k8s 自动分配
        assertThat(svc.getSpec().getPorts().get(0).getNodePort()).isNull();
    }

    // ---- Service 幂等：createOrReplace 撞 NodePort 的回归修复 ----

    @Test
    void deployStack_serviceAlreadyExistsWithSameSpec_isIdempotent() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        stubNamespace();
        stubDeployments();
        stubPvc();
        Services s = stubServicesList(Collections.emptyList());
        // 集群上已存在同 spec 的 Service（NodePort 30080）→ 幂等跳过，不重复创建
        io.fabric8.kubernetes.api.model.Service existing = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("nginx-80").endMetadata()
                .withNewSpec().withType("NodePort")
                .addNewPort().withPort(80).withProtocol("TCP").withNodePort(30080)
                .withTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(80)).endPort()
                .endSpec()
                .build();
        io.fabric8.kubernetes.client.dsl.ServiceResource existingRes = mock(
                io.fabric8.kubernetes.client.dsl.ServiceResource.class);
        when(s.svcNsOp.withName("nginx-80")).thenReturn(existingRes);
        when(existingRes.get()).thenReturn(existing);

        kubeStackService.deployStack(new DeployStackDto(1));

        // 未发生创建/删除（回归：createOrReplace 会报 nodePort already allocated）
        verify(s.svcNsOp, never()).resource(any());
        verify(existingRes, never()).delete();
    }

    @Test
    void deployStack_serviceSpecChanged_recreatesService() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        stubNamespace();
        stubDeployments();
        stubPvc();
        Services s = stubServicesList(Collections.emptyList());
        // 已存在但 NodePort 不同 → 删旧建新
        io.fabric8.kubernetes.api.model.Service stale = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata().withName("nginx-80").endMetadata()
                .withNewSpec().withType("NodePort")
                .addNewPort().withPort(80).withProtocol("TCP").withNodePort(30099)
                .withTargetPort(new io.fabric8.kubernetes.api.model.IntOrString(80)).endPort()
                .endSpec()
                .build();
        io.fabric8.kubernetes.client.dsl.ServiceResource staleRes = mock(
                io.fabric8.kubernetes.client.dsl.ServiceResource.class);
        when(s.svcNsOp.withName("nginx-80")).thenReturn(staleRes);
        when(staleRes.get()).thenReturn(stale);
        when(staleRes.delete()).thenReturn(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        verify(staleRes).delete();
        verify(s.svcNsOp).resource(any(io.fabric8.kubernetes.api.model.Service.class));
    }

    @Test
    void deployStack_serviceWithoutPorts_skipsServiceButCreatesDeployment() throws Exception {
        // 回归：无端口声明的服务不应创建 Service（k8s 拒绝 spec.ports 为空的 Service），Deployment 照常
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "worker", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(Collections.emptyList());
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        stubNamespace();
        Deps deps = stubDeployments();
        stubPvc();
        stubServicesList(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        verify(deps.nsOp, atLeastOnce()).resource(any(Deployment.class));
    }

    @Test
    void deployStack_removesOrphanServicesOfDeletedServices() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(volumeMapper.selectVolumesByServiceIds(java.util.List.of(1))).thenReturn(Collections.emptyList());

        stubNamespace();
        stubDeployments();
        stubPvc();
        // 集群上残留一个已删除服务的 Service（service_id=9 不在现存服务里）
        io.fabric8.kubernetes.api.model.Service stale = new io.fabric8.kubernetes.api.model.ServiceBuilder()
                .withNewMetadata()
                .withName("old")
                .addToLabels(Constants.STACK_ID_LABEL, "1")
                .addToLabels(Constants.SERVICE_ID_LABEL, "9")
                .endMetadata()
                .build();
        Services s = stubServicesList(Collections.emptyList());
        // 端口级 SVC 清理按 service 标签查（服务 1 无残留 → 空）；孤儿清理按 stack 标签查 → 返回 stale
        io.fabric8.kubernetes.client.dsl.NonNamespaceOperation orphanOp =
                mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        when(s.svcNsOp.withLabel(Constants.STACK_ID_LABEL, "1")).thenReturn(orphanOp);
        io.fabric8.kubernetes.api.model.ServiceList orphanList = mock(io.fabric8.kubernetes.api.model.ServiceList.class);
        when(orphanList.getItems()).thenReturn(List.of(stale));
        when(orphanOp.list()).thenReturn(orphanList);
        io.fabric8.kubernetes.client.dsl.ServiceResource staleRes = mock(
                io.fabric8.kubernetes.client.dsl.ServiceResource.class);
        when(s.svcNsOp.withName("old")).thenReturn(staleRes);
        when(staleRes.delete()).thenReturn(Collections.emptyList());

        kubeStackService.deployStack(new DeployStackDto(1));

        // 孤儿 Service 被清理（仅一次：端口级清理的 service 标签查不到它）
        verify(staleRes).delete();
    }

    // ---- 状态：readyReplicas → RUNNING / PARTIAL / STOPPED / NONE ----

    @Test
    void getStackStatus_mapsRunningReadyReplicas() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 2), service(2, "static", 1)));
        stubDeploymentsList(deployment(1, "nginx", "1", 2, 2),
                deployment(2, "static", "2", 1, 1));

        StackStatusVo vo = kubeStackService.getStackStatus(new DeployStackDto(1));

        assertThat(vo.getStatus()).isEqualTo("RUNNING");
        assertThat(vo.getServices()).hasSize(2);
        assertThat(vo.getServices().stream()
                .filter(s -> s.getServiceName().equals("nginx")).findFirst().orElseThrow()
                .getHealthyCount()).isEqualTo(2);
    }

    @Test
    void getStackStatus_mapsPartialAndStoppedAndNone() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 2), service(2, "static", 1), service(3, "gone", 1)));
        // nginx 2 目标 1 就绪 → PARTIAL；static 缩放为 0 → STOPPED；gone 无 deployment → NONE
        stubDeploymentsList(deployment(1, "nginx", "1", 2, 1),
                deployment(2, "static", "2", 0, 0));

        StackStatusVo vo = kubeStackService.getStackStatus(new DeployStackDto(1));

        assertThat(vo.getStatus()).isEqualTo("PARTIAL");
        assertThat(vo.getServices().stream()
                .filter(s -> s.getServiceName().equals("static")).findFirst().orElseThrow().getStatus())
                .isEqualTo("STOPPED");
        assertThat(vo.getServices().stream()
                .filter(s -> s.getServiceName().equals("gone")).findFirst().orElseThrow().getStatus())
                .isEqualTo("NONE");
    }

    // ---- 停止 / 下架 / 日志 ----

    @Test
    void stopStack_scalesAllDeploymentsToZero() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        Deps deps = stubDeployments();
        stubDeploymentsListOn(deps, deployment(1, "nginx", "1", 2, 2));
        RollableScalableResource scaleResource = mock(RollableScalableResource.class);
        when(deps.nsOp.withName("nginx")).thenReturn(scaleResource);

        kubeStackService.stopStack(new DeployStackDto(1));

        verify(scaleResource).scale(0);
        verify(historyService).createHistory(any());
    }

    @Test
    void removeStack_deletesDeploymentsAndServicesButKeepsPvc() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);
        Deps deps = stubDeployments();
        stubDeploymentsListOn(deps, deployment(1, "nginx", "1", 2, 2));
        RollableScalableResource<Deployment> deleteRes = mock(RollableScalableResource.class);
        when(deps.nsOp.withName("nginx")).thenReturn(deleteRes);
        when(deleteRes.delete()).thenReturn(Collections.emptyList());

        Services s = stubServicesList(Collections.emptyList());

        kubeStackService.removeStack(new DeployStackDto(1));

        verify(deleteRes).delete();
        // 服务列表为空 → 不实际删除任何 Service；PVC 从不删除（与 Docker named volume 语义一致）
        verify(s.svcNsOp, never()).withName(anyString());
        verify(client, never()).persistentVolumeClaims();
    }

    @Test
    void getContainerLog_trimsToTailAndPicksSortedPod() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(cluster());
        when(kubernetesClientFactory.getClient(cluster())).thenReturn(client);

        MixedOperation podOps = mock(MixedOperation.class);
        when(client.pods()).thenReturn(podOps);
        NonNamespaceOperation podNs = mock(NonNamespaceOperation.class);
        when(podOps.inNamespace("web")).thenReturn(podNs);
        when(podNs.withLabel(anyString(), anyString())).thenReturn(podNs);
        when(podNs.list()).thenReturn(new PodListBuilder()
                .withItems(
                        new PodBuilder().withNewMetadata().withName("nginx-abc").endMetadata().build(),
                        new PodBuilder().withNewMetadata().withName("nginx-def").endMetadata().build())
                .build());
        PodResource podRes = mock(PodResource.class);
        when(podNs.resource(any(io.fabric8.kubernetes.api.model.Pod.class))).thenReturn(podRes);
        when(podRes.getLog()).thenReturn("line1\nline2\nline3\nline4");

        ContainerLogVo vo = kubeStackService.getContainerLog(new ContainerLogsDto(1, 1, 1, 2));

        // 排序后副本 1 → "nginx-def"，日志截取最后 2 行
        assertThat(vo.getContainerName()).isEqualTo("nginx-def");
        assertThat(vo.getLog()).isEqualTo("line3\nline4");
    }

    // ---- helpers ----

    private StackDo stack() {
        return StackDo.builder().id(1).clusterId(1).stackName("web")
                .status(DataStatus.EXIST.ordinal()).build();
    }

    private ServiceDo service(int id, String name, int replicas) {
        return ServiceDo.builder().id(id).stackId(1).serviceName(name).replicas(replicas)
                .image("nginx:latest").build();
    }

    private PortDo port(String protocol, int port) {
        return PortDo.builder().protocol(protocol).port(port).build();
    }

    private PortDo port(String protocol, int port, String serviceType) {
        return PortDo.builder().protocol(protocol).port(port).serviceType(serviceType).build();
    }

    private ClusterDo cluster() {
        return ClusterDo.builder().id(1).type("K8S").status(DataStatus.EXIST.ordinal()).build();
    }

    private Deployment deployment(int serviceId, String name, String serviceLabel, int replicas, int ready) {
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .addToLabels(Constants.STACK_ID_LABEL, "1")
                .addToLabels(Constants.SERVICE_ID_LABEL, serviceLabel)
                .endMetadata()
                .withSpec(new DeploymentSpecBuilder().withReplicas(replicas).build())
                .withStatus(new DeploymentStatusBuilder().withReadyReplicas(ready).build())
                .build();
    }

    /**
     * 状态查询/停止/下架共用的 Deployment 列表链：withLabel(...).list() 返回给定 Deployment
     */
    private void stubDeploymentsList(Deployment... deployments) {
        Deps deps = stubDeployments();
        stubDeploymentsListOn(deps, deployments);
    }

    private void stubDeploymentsListOn(Deps deps, Deployment... deployments) {
        when(deps.nsOp.withLabel(anyString(), anyString())).thenReturn(deps.nsOp);
        when(deps.nsOp.list()).thenReturn(new DeploymentListBuilder().withItems(deployments).build());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubNamespace() {
        // client.namespaces() 返回 MixedOperation，withName/resource 均直接在其上调用
        MixedOperation<Namespace, NamespaceList, Resource<Namespace>> nsOps = mock(MixedOperation.class);
        lenient().when(client.namespaces()).thenReturn(nsOps);
        Resource<Namespace> nsGet = mock(Resource.class);
        lenient().when(nsOps.withName("web")).thenReturn(nsGet);
        lenient().when(nsGet.get()).thenReturn(null);
        Resource<Namespace> nsRes = mock(Resource.class);
        lenient().when(nsOps.resource(any(Namespace.class))).thenReturn(nsRes);
        lenient().when(nsRes.create()).thenReturn(mock(Namespace.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Deps stubDeployments() {
        AppsAPIGroupDSL apps = mock(AppsAPIGroupDSL.class);
        lenient().when(client.apps()).thenReturn(apps);
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> depOps = mock(MixedOperation.class);
        lenient().when(apps.deployments()).thenReturn(depOps);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> nsOp =
                mock(NonNamespaceOperation.class);
        lenient().when(depOps.inNamespace("web")).thenReturn(nsOp);
        RollableScalableResource<Deployment> depRes = mock(RollableScalableResource.class);
        lenient().when(nsOp.resource(any(Deployment.class))).thenReturn(depRes);
        lenient().when(depRes.createOrReplace()).thenReturn(mock(Deployment.class));
        // 孤儿清理与部署末段状态查询共用该链，默认空列表；状态/停止测试会覆盖为具体 items
        lenient().when(nsOp.withLabel(anyString(), anyString())).thenReturn(nsOp);
        lenient().when(nsOp.list()).thenReturn(new DeploymentListBuilder().build());
        Deps deps = new Deps();
        deps.nsOp = nsOp;
        return deps;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubPvc() {
        MixedOperation<PersistentVolumeClaim, io.fabric8.kubernetes.api.model.PersistentVolumeClaimList,
                Resource<PersistentVolumeClaim>> pvcOps = mock(MixedOperation.class);
        lenient().when(client.persistentVolumeClaims()).thenReturn(pvcOps);
        NonNamespaceOperation<PersistentVolumeClaim, io.fabric8.kubernetes.api.model.PersistentVolumeClaimList,
                Resource<PersistentVolumeClaim>> pvcNs = mock(NonNamespaceOperation.class);
        lenient().when(pvcOps.inNamespace("web")).thenReturn(pvcNs);
        Resource<PersistentVolumeClaim> pvcGet = mock(Resource.class);
        lenient().when(pvcNs.withName(anyString())).thenReturn(pvcGet);
        lenient().when(pvcGet.get()).thenReturn(null);
        Resource<PersistentVolumeClaim> pvcRes = mock(Resource.class);
        lenient().when(pvcNs.resource(any())).thenReturn(pvcRes);
        lenient().when(pvcRes.create()).thenReturn(mock(PersistentVolumeClaim.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Services stubServicesList(List<Service> items) {
        MixedOperation<Service, ServiceList, ServiceResource<Service>> svcOps = mock(MixedOperation.class);
        lenient().when(client.services()).thenReturn(svcOps);
        NonNamespaceOperation<Service, ServiceList, ServiceResource<Service>> svcNsOp = mock(NonNamespaceOperation.class);
        lenient().when(svcOps.inNamespace("web")).thenReturn(svcNsOp);
        ServiceResource<Service> svcRes = mock(ServiceResource.class);
        lenient().when(svcNsOp.resource(any(Service.class))).thenReturn(svcRes);
        lenient().when(svcRes.create()).thenReturn(mock(Service.class));
        // upsertService 先 withName(name).get() 判幂等：默认不存在 → 走 create
        ServiceResource<Service> svcGet = mock(ServiceResource.class);
        lenient().when(svcNsOp.withName(anyString())).thenReturn(svcGet);
        lenient().when(svcGet.get()).thenReturn(null);
        lenient().when(svcNsOp.withLabel(anyString(), anyString())).thenReturn(svcNsOp);
        ServiceList list = mock(ServiceList.class);
        lenient().when(list.getItems()).thenReturn(items);
        lenient().when(svcNsOp.list()).thenReturn(list);
        Services s = new Services();
        s.svcNsOp = svcNsOp;
        return s;
    }

    private static class Deps {
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> nsOp;
    }

    private static class Services {
        NonNamespaceOperation<Service, ServiceList, ServiceResource<Service>> svcNsOp;
    }
}