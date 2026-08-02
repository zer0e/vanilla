package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;

/**
 * Kubernetes 运行时栈操作：部署 / 状态 / 停止 / 下架 / 日志。
 * 与 DeployService 保持相同语义，DeployServiceImpl 对 K8S 类型集群做转发
 */
public interface KubernetesStackService {

    StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException;

    StackStatusVo getStackStatus(DeployStackDto deployStackDto) throws BusinessException;

    void stopStack(DeployStackDto deployStackDto) throws BusinessException;

    void removeStack(DeployStackDto deployStackDto) throws BusinessException;

    ContainerLogVo getContainerLog(ContainerLogsDto containerLogsDto) throws BusinessException;

    boolean isKubernetes(Integer stackId) throws BusinessException;
}