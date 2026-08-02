package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.DeployPreviewVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface DeployService {

    StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException;

    StackStatusVo getStackStatus(DeployStackDto deployStackDto) throws BusinessException;

    void stopStack(DeployStackDto deployStackDto) throws BusinessException;

    void removeStack(DeployStackDto deployStackDto) throws BusinessException;

    /**
     * 查看服务某个副本容器的最近日志
     */
    ContainerLogVo getContainerLog(ContainerLogsDto containerLogsDto) throws BusinessException;

    /**
     * 预览部署资源（K8s 返回 YAML；Docker 不支持）
     */
    DeployPreviewVo preview(DeployStackDto deployStackDto) throws BusinessException;
}
