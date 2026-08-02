package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

@Data
public class PortVo {
    private Integer id;

    private Integer serviceId;

    private Integer stackId;

    private String protocol;

    private Integer port;

    /**
     * K8s Service 类型：ClusterIP / NodePort / LoadBalancer；空 = 自动
     */
    private String serviceType;

    /**
     * 所属服务名（栈级端口管理页展示用）
     */
    private String serviceName;
}
