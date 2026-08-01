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

    private Integer replicas;
}
