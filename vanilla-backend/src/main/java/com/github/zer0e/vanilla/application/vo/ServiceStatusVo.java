package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

import java.util.List;

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

    /**
     * 暴露地址列表（如 NodePort "nodeIP:30080" / LoadBalancer "1.2.3.4:80" / Docker "0.0.0.0:8080"），未部署为空
     */
    private List<String> exposedAddresses;
}
