package com.github.zer0e.vanilla.application.dto;

import com.github.zer0e.vanilla.domain.Env;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateServiceDto {

    @NotNull(message = "服务id不能为空")
    private Integer id;

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    /**
     * 镜像
     */
    @NotEmpty(message = "服务镜像不能为空")
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
     * 健康检查命令（Docker HEALTHCHECK）
     */
    private String healthCheckCmd;

    /**
     * 更新策略 PollingUpdate
     */
    private String strategy;

    /**
     * 引用的栈级卷 id 列表；null 表示不修改引用
     */
    private List<Integer> volumeIds;

    /**
     * 声明的端口列表；null 表示不修改端口（新增/移除端口在保存时合并同步）
     */
    private List<PortSpecDto> ports;

    private List<Env> envs;
}
