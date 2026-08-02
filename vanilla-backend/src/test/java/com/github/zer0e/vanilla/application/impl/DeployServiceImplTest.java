package com.github.zer0e.vanilla.application.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.KubernetesStackService;
import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.ServiceStatusVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ClusterMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.VolumeMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import com.github.zer0e.vanilla.infrastructure.docker.DockerClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeployServiceImpl 核心逻辑单元测试，锁住回归修复的行为：
 * 端口绑定写入 HostConfig、宿主端口全局预校验、容器命名、状态映射、失败回滚
 */
@ExtendWith(MockitoExtension.class)
class DeployServiceImplTest {

    @Mock
    private DockerClientFactory dockerClientFactory;
    @Mock
    private StackMapper stackMapper;
    @Mock
    private ClusterMapper clusterMapper;
    @Mock
    private KubernetesStackService kubernetesStackService;
    @Mock
    private ServiceMapper serviceMapper;
    @Mock
    private PortMapper portMapper;
    @Mock
    private VolumeMapper volumeMapper;
    @Mock
    private HistoryService historyService;
    @Mock
    private DockerClient dockerClient;

    private DeployServiceImpl deployService;

    @BeforeEach
    void setUp() {
        deployService = new DeployServiceImpl(
                dockerClientFactory, stackMapper, clusterMapper, serviceMapper, portMapper,
                volumeMapper, historyService, kubernetesStackService);
    }

    // ---- buildHostConfig：端口绑定必须存在于 HostConfig（修复被 withHostConfig 覆盖的缺陷） ----

    @Test
    void buildHostConfig_bindsPortsIntoHostConfigWithReplicaOffset() {
        ServiceDo service = ServiceDo.builder().id(1).cpu(512).memory(128).build();
        List<PortDo> ports = List.of(port("tcp", 80));
        List<ExposedPort> exposed = new ArrayList<>();

        HostConfig hc = deployService.buildHostConfig(service, ports, 1, exposed);

        // 端口绑定写入 HostConfig，宿主端口 = 声明端口 + 副本索引(1) => 81
        assertThat(hc.getPortBindings()).isNotNull();
        assertThat(hc.getPortBindings().getBindings()).containsKey(ExposedPort.tcp(80));
        assertThat(hc.getPortBindings().getBindings().get(ExposedPort.tcp(80))[0].getHostPortSpec())
                .isEqualTo("81");
        // 容器端口被收集用于 withExposedPorts
        assertThat(exposed).containsExactly(ExposedPort.tcp(80));
        // 资源限制写入同一 HostConfig
        assertThat(hc.getCpuShares()).isEqualTo(512L);
        assertThat(hc.getMemory()).isEqualTo(128L * 1024L * 1024L);
    }

    @Test
    void buildHostConfig_replicaIndexNull_usesDeclaredPort() {
        ServiceDo service = ServiceDo.builder().id(1).build();
        List<ExposedPort> exposed = new ArrayList<>();

        HostConfig hc = deployService.buildHostConfig(service, List.of(port("tcp", 80)), null, exposed);

        assertThat(hc.getPortBindings().getBindings().get(ExposedPort.tcp(80))[0].getHostPortSpec())
                .isEqualTo("80");
        assertThat(exposed).containsExactly(ExposedPort.tcp(80));
    }

    @Test
    void buildHostConfig_noPorts_noBindings() {
        ServiceDo service = ServiceDo.builder().id(1).build();
        List<ExposedPort> exposed = new ArrayList<>();

        HostConfig hc = deployService.buildHostConfig(service, Collections.emptyList(), null, exposed);

        assertThat(hc.getPortBindings()).isNull();
        assertThat(exposed).isEmpty();
    }

    // ---- buildVolumeBinds：卷挂载绑定 ----

    @Test
    void buildVolumeBinds_mapsDockerVolumeToContainerPath() {
        VolumeDo data = VolumeDo.builder().stackId(1).serviceId(1).volumeName("data").mountPath("/data").build();

        List<Bind> binds = deployService.buildVolumeBinds(1, 1, List.of(data));

        assertThat(binds).hasSize(1);
        // Docker named volume = vanilla-{stackId}-{serviceId}-{volumeName}
        assertThat(binds.get(0).getPath()).isEqualTo("vanilla-1-1-data");
        // 容器内挂载路径
        assertThat(binds.get(0).getVolume().getPath()).isEqualTo("/data");
    }

    @Test
    void buildVolumeBinds_empty_returnsEmpty() {
        assertThat(deployService.buildVolumeBinds(1, 1, Collections.emptyList())).isEmpty();
    }

    // ---- validateHostPorts：跨服务/副本宿主端口冲突预校验 ----

    @Test
    void validateHostPorts_allowsDistinctPorts() {
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(portMapper.selectPortsByServiceId(2)).thenReturn(List.of(port("tcp", 82)));

        assertDoesNotThrow(() -> deployService.validateHostPorts(1,
                List.of(service(1, "nginx", 2), service(2, "static", 1))));
    }

    @Test
    void validateHostPorts_rejectsCrossServiceCollision() {
        // nginx replicas=3 => 宿主端口 80/81/82；static port 82 => 冲突
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(portMapper.selectPortsByServiceId(2)).thenReturn(List.of(port("tcp", 82)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deployService.validateHostPorts(1,
                        List.of(service(1, "nginx", 3), service(2, "static", 1))));

        assertThat(ex.getMessage()).contains("宿主端口");
    }

    // ---- containerName：命名规范与非法字符清洗 ----

    @Test
    void containerName_formatsWithAndWithoutIndex() {
        assertThat(deployService.containerName(1, "nginx", null)).isEqualTo("vanilla-1-nginx");
        assertThat(deployService.containerName(1, "nginx", 0)).isEqualTo("vanilla-1-nginx-0");
        assertThat(deployService.containerName(1, "nginx", 1)).isEqualTo("vanilla-1-nginx-1");
    }

    @Test
    void containerName_sanitizesInvalidCharacters() {
        assertThat(deployService.containerName(1, "My App", null)).isEqualTo("vanilla-1-my-app");
    }

    // ---- 状态映射 ----

    @Test
    void resolveStatus_mapsContainerStates() {
        assertThat(deployService.resolveStatus(0, 0)).isEqualTo("NONE");
        assertThat(deployService.resolveStatus(2, 2)).isEqualTo("RUNNING");
        assertThat(deployService.resolveStatus(2, 0)).isEqualTo("STOPPED");
        assertThat(deployService.resolveStatus(2, 1)).isEqualTo("PARTIAL");
    }

    @Test
    void resolveStackStatus_mapsStackStates() {
        assertThat(deployService.resolveStackStatus(List.of())).isEqualTo("NONE");
        assertThat(deployService.resolveStackStatus(List.of(serviceStatus("NONE")))).isEqualTo("NONE");
        assertThat(deployService.resolveStackStatus(List.of(serviceStatus("RUNNING"), serviceStatus("RUNNING"))))
                .isEqualTo("RUNNING");
        assertThat(deployService.resolveStackStatus(List.of(serviceStatus("STOPPED"), serviceStatus("NONE"))))
                .isEqualTo("STOPPED");
        assertThat(deployService.resolveStackStatus(List.of(serviceStatus("RUNNING"), serviceStatus("STOPPED"))))
                .isEqualTo("PARTIAL");
    }

    // ---- deployStack 整体流程 ----

    @Test
    void deployStack_portCollision_failsBeforeAnyContainerCreated() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(dockerClientFactory.getClient(1)).thenReturn(dockerClient);
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 3), service(2, "static", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        when(portMapper.selectPortsByServiceId(2)).thenReturn(List.of(port("tcp", 82)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deployService.deployStack(new DeployStackDto(1)));

        assertThat(ex.getMessage()).contains("宿主端口");
        // 校验失败发生在创建容器之前
        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void deployStack_midDeployFailure_rollsBackContainers() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(dockerClientFactory.getClient(1)).thenReturn(dockerClient);
        // 回滚时清理同栈容器
        stubListContainers(List.of(container("abc")));
        when(serviceMapper.selectServicesByStackIdAndSearch(1, null))
                .thenReturn(List.of(service(1, "nginx", 1)));
        when(portMapper.selectPortsByServiceId(1)).thenReturn(List.of(port("tcp", 80)));
        // 镜像拉取失败
        PullImageCmd pullCmd = mock(PullImageCmd.class);
        when(dockerClient.pullImageCmd("nginx:latest")).thenReturn(pullCmd);
        when(pullCmd.start()).thenThrow(new RuntimeException("pull fail"));

        assertThrows(BusinessException.class, () -> deployService.deployStack(new DeployStackDto(1)));

        // 失败后回滚清理了同栈容器
        verify(dockerClient).removeContainerCmd(anyString());
    }

    // ---- Recreate 策略：先删后建 ----

    @Test
    void recreateService_removesExistingThenCreatesAllReplicas() {
        ServiceDo service = service(1, "nginx", 2);
        stubCreateContainer("new0", "new1");
        List<Container> existing = List.of(
                container("old0", "/vanilla-1-nginx-0"),
                container("old1", "/vanilla-1-nginx-1"));

        deployService.recreateService(dockerClient, stack(), service, List.of(port("tcp", 80)),
                Collections.emptyList(), 2, existing);

        verify(dockerClient).removeContainerCmd("old0");
        verify(dockerClient).removeContainerCmd("old1");
        verify(dockerClient, times(2)).createContainerCmd("nginx:latest");
        verify(dockerClient, times(2)).startContainerCmd(anyString());
    }

    // ---- RollingUpdate 策略：逐副本替换 ----

    @Test
    void rollingUpdateService_replacesOneReplicaAtATime() {
        ServiceDo service = service(1, "nginx", 2);
        stubCreateContainer("new0", "new1");
        List<Container> existing = List.of(
                container("old0", "/vanilla-1-nginx-0"),
                container("old1", "/vanilla-1-nginx-1"));

        deployService.rollingUpdateService(dockerClient, stack(), service, List.of(port("tcp", 80)),
                Collections.emptyList(), 2, existing);

        InOrder inOrder = inOrder(dockerClient);
        // 逐副本：停旧副本0 → 建新副本0 → 停旧副本1 → 建新副本1（其余副本持续服务）
        inOrder.verify(dockerClient).removeContainerCmd("old0");
        inOrder.verify(dockerClient).createContainerCmd("nginx:latest");
        inOrder.verify(dockerClient).removeContainerCmd("old1");
        inOrder.verify(dockerClient).createContainerCmd("nginx:latest");
    }

    @Test
    void rollingUpdateService_scaleChange_fallsBackToFullRecreate() {
        ServiceDo service = service(1, "nginx", 1); // 目标 1 副本，现有 2 → 缩容
        stubCreateContainer("new0");
        List<Container> existing = List.of(
                container("old0", "/vanilla-1-nginx-0"),
                container("old1", "/vanilla-1-nginx-1"));

        deployService.rollingUpdateService(dockerClient, stack(), service, List.of(port("tcp", 80)),
                Collections.emptyList(), 1, existing);

        // 副本数变化退化为全量重建：删掉全部旧容器，创建 1 个新容器
        verify(dockerClient).removeContainerCmd("old0");
        verify(dockerClient).removeContainerCmd("old1");
        verify(dockerClient).createContainerCmd("nginx:latest");
    }

    // ---- 健康检查：Docker HEALTHCHECK 构建与健康统计 ----

    @Test
    void buildHealthCheck_configured_buildsCmdShellProbe() {
        ServiceDo service = ServiceDo.builder().healthCheckCmd("curl -f http://localhost/health || exit 1").build();

        HealthCheck hc = deployService.buildHealthCheck(service);

        assertThat(hc).isNotNull();
        assertThat(hc.getTest()).containsExactly("CMD-SHELL", "curl -f http://localhost/health || exit 1");
        assertThat(hc.getInterval()).isEqualTo(java.time.Duration.ofSeconds(30).toNanos());
        assertThat(hc.getTimeout()).isEqualTo(java.time.Duration.ofSeconds(10).toNanos());
        assertThat(hc.getRetries()).isEqualTo(3);
        assertThat(hc.getStartPeriod()).isEqualTo(java.time.Duration.ofSeconds(5).toNanos());
    }

    @Test
    void buildHealthCheck_notConfigured_returnsNull() {
        assertThat(deployService.buildHealthCheck(ServiceDo.builder().build())).isNull();
    }

    @Test
    void countHealthy_countsOnlyHealthyRunningContainers() {
        InspectContainerResponse healthyResp = inspectResponse("healthy");
        InspectContainerResponse unhealthyResp = inspectResponse("unhealthy");
        InspectContainerCmd inspect0 = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("c0")).thenReturn(inspect0);
        when(inspect0.exec()).thenReturn(healthyResp);
        InspectContainerCmd inspect1 = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("c1")).thenReturn(inspect1);
        when(inspect1.exec()).thenReturn(unhealthyResp);

        Container runningHealthy = container("c0");
        when(runningHealthy.getState()).thenReturn("running");
        Container runningUnhealthy = container("c1");
        when(runningUnhealthy.getState()).thenReturn("running");
        Container stopped = container("c2");
        when(stopped.getState()).thenReturn("exited");

        // c0 运行且 healthy → 计健康；c1 运行但 unhealthy → 不计；c2 停止 → 不 inspect
        assertThat(deployService.countHealthy(dockerClient, List.of(runningHealthy, runningUnhealthy, stopped)))
                .isEqualTo(1);
        verify(dockerClient, never()).inspectContainerCmd("c2");
    }

    @Test
    void countHealthy_noHealthInfo_treatsRunningAsHealthy() {
        // 未配置健康检查 / 尚未产生健康状态时，运行即视为健康，且不因异常中断
        InspectContainerResponse resp = inspectResponse(null);
        InspectContainerCmd inspect = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("c0")).thenReturn(inspect);
        when(inspect.exec()).thenReturn(resp);

        Container running = container("c0");
        when(running.getState()).thenReturn("running");

        assertThat(deployService.countHealthy(dockerClient, List.of(running))).isEqualTo(1);
    }

    private InspectContainerResponse inspectResponse(String healthStatus) {
        ContainerState state = mock(ContainerState.class);
        if (healthStatus == null) {
            when(state.getHealth()).thenReturn(null);
        } else {
            HealthState health = mock(HealthState.class);
            when(health.getStatus()).thenReturn(healthStatus);
            when(state.getHealth()).thenReturn(health);
        }
        InspectContainerResponse resp = mock(InspectContainerResponse.class);
        when(resp.getState()).thenReturn(state);
        return resp;
    }

    // ---- 运行时分流：K8S 集群委托 KubernetesStackService ----

    @Test
    void deployStack_k8sCluster_delegatesToKubernetesStackService() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(clusterMapper.selectById(1)).thenReturn(ClusterDo.builder().id(1).type("K8S").build());
        StackStatusVo vo = mock(StackStatusVo.class);
        when(kubernetesStackService.deployStack(new DeployStackDto(1))).thenReturn(vo);

        StackStatusVo result = deployService.deployStack(new DeployStackDto(1));

        assertThat(result).isSameAs(vo);
        verify(kubernetesStackService).deployStack(new DeployStackDto(1));
        // K8S 集群不触碰 Docker 连接
        verify(dockerClientFactory, never()).getClient(anyInt());
    }

    // ---- 容器日志：按副本索引选容器、默认取运行副本、未部署报错 ----

    @Test
    void getContainerLog_returnsLogOfRequestedReplica() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(dockerClientFactory.getClient(1)).thenReturn(dockerClient);
        stubLogFetch("c1", "GET / HTTP/1.1\" 200");
        stubListContainers(List.of(
                container("c0", "/vanilla-1-nginx-0"),
                container("c1", "/vanilla-1-nginx-1")));

        ContainerLogVo vo = deployService.getContainerLog(new ContainerLogsDto(1, 1, 1, 100));

        assertThat(vo.getContainerId()).isEqualTo("c1");
        assertThat(vo.getContainerName()).isEqualTo("vanilla-1-nginx-1");
        assertThat(vo.getLog()).isEqualTo("GET / HTTP/1.1\" 200");
        verify(dockerClient).logContainerCmd("c1");
    }

    @Test
    void getContainerLog_noReplicaIndex_prefersRunningContainer() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(dockerClientFactory.getClient(1)).thenReturn(dockerClient);
        stubLogFetch("c1", "log of running replica");
        // c0 停止，c1 运行 → 默认查看运行中的副本
        Container c0 = container("c0", "/vanilla-1-nginx-0");
        when(c0.getState()).thenReturn("exited");
        Container c1 = container("c1", "/vanilla-1-nginx-1");
        when(c1.getState()).thenReturn("running");
        stubListContainers(List.of(c0, c1));

        ContainerLogVo vo = deployService.getContainerLog(new ContainerLogsDto(1, 1, null, 100));

        assertThat(vo.getContainerId()).isEqualTo("c1");
        verify(dockerClient).logContainerCmd("c1");
    }

    @Test
    void getContainerLog_serviceNotDeployed_throws() throws Exception {
        when(stackMapper.selectById(1)).thenReturn(stack());
        when(dockerClientFactory.getClient(1)).thenReturn(dockerClient);
        stubListContainers(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> deployService.getContainerLog(new ContainerLogsDto(1, 1, null, 100)));

        assertThat(ex.getMessage()).contains("未部署");
        verify(dockerClient, never()).logContainerCmd(anyString());
    }

    // ---- helpers ----

    /**
     * stub docker 日志读取链路：logContainerCmd → LogContainerResultCallback.toString()
     */
    private void stubLogFetch(String containerId, String logText) throws Exception {
        LogContainerCmd logCmd = mock(LogContainerCmd.class);
        when(dockerClient.logContainerCmd(containerId)).thenReturn(logCmd);
        when(logCmd.withStdOut(true)).thenReturn(logCmd);
        when(logCmd.withStdErr(true)).thenReturn(logCmd);
        when(logCmd.withTail(anyInt())).thenReturn(logCmd);
        LogContainerResultCallback callback = mock(LogContainerResultCallback.class);
        when(logCmd.exec(org.mockito.ArgumentMatchers.any(LogContainerResultCallback.class))).thenReturn(callback);
        when(callback.toString()).thenReturn(logText);
    }

    @SafeVarargs
    private final void stubListContainers(List<Container>... responses) {
        ListContainersCmd listCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(true)).thenReturn(listCmd);
        when(listCmd.withLabelFilter(anyMap())).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(responses[0], Arrays.copyOfRange(responses, 1, responses.length));
    }

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

    private Container container(String id) {
        Container c = mock(Container.class);
        lenient().when(c.getId()).thenReturn(id);
        return c;
    }

    private Container container(String id, String name) {
        Container c = container(id);
        lenient().when(c.getNames()).thenReturn(new String[]{name});
        return c;
    }

    /**
     * stub docker 创建/启动/删除容器链路。ids 为每次 exec() 返回的容器 id（按调用顺序）
     */
    private void stubCreateContainer(String... ids) {
        CreateContainerCmd cmd = mock(CreateContainerCmd.class);
        lenient().when(dockerClient.createContainerCmd(anyString())).thenReturn(cmd);
        lenient().when(cmd.withName(anyString())).thenReturn(cmd);
        lenient().when(cmd.withLabels(anyMap())).thenReturn(cmd);
        lenient().when(cmd.withEnv(anyList())).thenReturn(cmd);
        lenient().when(cmd.withExposedPorts(anyList())).thenReturn(cmd);
        lenient().when(cmd.withHostConfig(org.mockito.ArgumentMatchers.any(HostConfig.class))).thenReturn(cmd);
        lenient().when(cmd.withCmd(org.mockito.ArgumentMatchers.any(String[].class))).thenReturn(cmd);
        CreateContainerResponse[] responses = ids.length == 0
                ? new CreateContainerResponse[]{response("created")}
                : Arrays.stream(ids).map(this::response).toArray(CreateContainerResponse[]::new);
        lenient().when(cmd.exec()).thenReturn(responses[0],
                Arrays.copyOfRange(responses, 1, responses.length));
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        lenient().when(dockerClient.startContainerCmd(anyString())).thenReturn(startCmd);
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        lenient().when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeCmd);
        lenient().when(removeCmd.withForce(true)).thenReturn(removeCmd);
    }

    private CreateContainerResponse response(String id) {
        CreateContainerResponse resp = mock(CreateContainerResponse.class);
        when(resp.getId()).thenReturn(id);
        return resp;
    }

    private ServiceStatusVo serviceStatus(String status) {
        ServiceStatusVo vo = new ServiceStatusVo();
        vo.setStatus(status);
        return vo;
    }
}
