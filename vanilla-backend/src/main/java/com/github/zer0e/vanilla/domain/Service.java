package com.github.zer0e.vanilla.domain;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
public class Service {
    private Integer id;

    private Integer stackId;
    private String serviceName;
    /**
     * 镜像
     */
    private String image;
    /**
     * 副本数
     */
    private Integer replicas;

    private String command;

    private String args;

    private Integer cpu;
    private Integer memory;


    private String hostname;
    /**
     * 停止宽限时长
     */
    private String terminationGracePeriodSeconds;

    /**
     * 健康检查命令（Docker HEALTHCHECK，如 curl -f http://localhost/health || exit 1）
     */
    private String healthCheckCmd;

    /**
     * 更新策略 PollingUpdate
     */
    private String strategy;

    /**
     * K8s Service 类型：ClusterIP / NodePort / LoadBalancer；空 = 自动（有 NodePort 需求则 NodePort，否则 ClusterIP）
     */
    private String serviceType;

    private List<Env> envs;
}
