package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

/**
 * 容器日志
 */
@Data
public class ContainerLogVo {
    private String containerId;
    private String containerName;
    private String log;
}