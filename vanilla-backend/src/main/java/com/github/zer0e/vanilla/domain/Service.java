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
     * 更新策略 PollingUpdate
     */
    private String strategy;

    private List<Env> envs;
}
