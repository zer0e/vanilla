package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

/**
 * 服务运行状态
 */
@Data
public class ServiceStatusVo {
    private Integer serviceId;
    private String serviceName;

    /**
     * RUNNING / STOPPED / PARTIAL / NONE
     */
    private String status;

    private Integer runningCount;

    /**
     * 健康数（配置了 healthCheckCmd 时按容器健康状态统计，否则等于 runningCount）
     */
    private Integer healthyCount;

    private Integer replicas;
}
