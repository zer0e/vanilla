package com.github.zer0e.vanilla.application.impl;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.zer0e.vanilla.application.DeployService;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ServiceStatusVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.domain.Env;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.docker.DockerClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeployServiceImpl implements DeployService {

    private final DockerClientFactory dockerClientFactory;
    private final StackMapper stackMapper;
    private final ServiceMapper serviceMapper;
    private final PortMapper portMapper;
    private final HistoryService historyService;

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deployStackDto.stackId + '_stack_admin')")
    public StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException {
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        removeStackContainers(client, stack.getId());
        List<ServiceDo> services = serviceMapper.selectServicesByStackIdAndSearch(stack.getId(), null);
        for (ServiceDo service : services) {
            deployService(client, stack, service);
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
        StackDo stack = getStack(deployStackDto.getStackId());
        DockerClient client = dockerClientFactory.getClient(stack.getClusterId());
        removeStackContainers(client, stack.getId());
        recordHistory(stack.getId(), "下架栈 " + stack.getStackName());
    }

    /**
     * 部署单个服务，按副本数创建并启动容器
     */
    private void deployService(DockerClient client, StackDo stack, ServiceDo service) throws BusinessException {
        try {
            pullImage(client, service.getImage());
            List<PortDo> ports = portMapper.selectPortsByServiceId(service.getId());
            int replicas = service.getReplicas() == null ? 1 : Math.max(1, service.getReplicas());
            for (int i = 0; i < replicas; i++) {
                String containerName = containerName(stack.getId(), service.getServiceName(), replicas > 1 ? i : null);
                CreateContainerResponse response = createContainer(client, stack, service, ports, containerName);
                client.startContainerCmd(response.getId()).exec();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("deploy service err, stackId={}, service={}", stack.getId(), service.getServiceName(), e);
            throw new BusinessException(Constants.DEPLOY_FAIL + "：" + service.getServiceName() + " " + e.getMessage());
        }
    }

    private CreateContainerResponse createContainer(DockerClient client, StackDo stack, ServiceDo service,
                                                    List<PortDo> ports, String containerName) {
        CreateContainerCmd cmd = client.createContainerCmd(service.getImage())
                .withName(containerName)
                .withEnv(buildEnvs(service.getEnvs()))
                .withLabels(buildLabels(stack.getId(), service));
        buildCommand(cmd, service);

        if (!CollectionUtils.isEmpty(ports)) {
            List<ExposedPort> exposedPortList = new ArrayList<>();
            Ports portBindings = new Ports();
            for (PortDo port : ports) {
                ExposedPort exposedPort = "udp".equalsIgnoreCase(port.getProtocol())
                        ? ExposedPort.udp(port.getPort()) : ExposedPort.tcp(port.getPort());
                exposedPortList.add(exposedPort);
                portBindings.bind(exposedPort, Ports.Binding.bindPort(port.getPort()));
            }
            cmd.withExposedPorts(exposedPortList);
            cmd.withPortBindings(portBindings);
        }

        HostConfig hostConfig = HostConfig.newHostConfig();
        if (service.getCpu() != null) {
            hostConfig.withCpuShares(service.getCpu());
        }
        if (service.getMemory() != null) {
            hostConfig.withMemory(service.getMemory() * 1024L * 1024L);
        }
        cmd.withHostConfig(hostConfig);
        return cmd.exec();
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

    private Map<String, String> buildLabels(Integer stackId, ServiceDo service) {
        Map<String, String> labels = new HashMap<>();
        labels.put(Constants.STACK_ID_LABEL, String.valueOf(stackId));
        labels.put(Constants.SERVICE_ID_LABEL, String.valueOf(service.getId()));
        return labels;
    }

    private String containerName(Integer stackId, String serviceName, Integer index) {
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

    private String resolveStatus(int total, long running) {
        if (total == 0) {
            return "NONE";
        }
        if (running == total) {
            return "RUNNING";
        }
        if (running == 0) {
            return "STOPPED";
        }
        return "PARTIAL";
    }

    private String resolveStackStatus(List<ServiceStatusVo> services) {
        if (services.isEmpty() || services.stream().allMatch(s -> "NONE".equals(s.getStatus()))) {
            return "NONE";
        }
        if (services.stream().allMatch(s -> "RUNNING".equals(s.getStatus()))) {
            return "RUNNING";
        }
        if (services.stream().allMatch(s -> "STOPPED".equals(s.getStatus()) || "NONE".equals(s.getStatus()))) {
            return "STOPPED";
        }
        return "PARTIAL";
    }

    private StackDo getStack(Integer stackId) throws BusinessException {
        StackDo stack = stackMapper.selectById(stackId);
        if (stack == null || stack.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        return stack;
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}
