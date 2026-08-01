package com.github.zer0e.vanilla.application.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ServiceStatusVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
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
    private ServiceMapper serviceMapper;
    @Mock
    private PortMapper portMapper;
    @Mock
    private HistoryService historyService;
    @Mock
    private DockerClient dockerClient;

    private DeployServiceImpl deployService;

    @BeforeEach
    void setUp() {
        deployService = new DeployServiceImpl(
                dockerClientFactory, stackMapper, serviceMapper, portMapper, historyService);
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

        deployService.recreateService(dockerClient, stack(), service, List.of(port("tcp", 80)), 2, existing);

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

        deployService.rollingUpdateService(dockerClient, stack(), service, List.of(port("tcp", 80)), 2, existing);

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

        deployService.rollingUpdateService(dockerClient, stack(), service, List.of(port("tcp", 80)), 1, existing);

        // 副本数变化退化为全量重建：删掉全部旧容器，创建 1 个新容器
        verify(dockerClient).removeContainerCmd("old0");
        verify(dockerClient).removeContainerCmd("old1");
        verify(dockerClient).createContainerCmd("nginx:latest");
    }

    // ---- helpers ----

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
        when(c.getId()).thenReturn(id);
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
