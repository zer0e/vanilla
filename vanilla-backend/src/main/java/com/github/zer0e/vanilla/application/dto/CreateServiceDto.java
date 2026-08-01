package com.github.zer0e.vanilla.application.dto;

import com.github.zer0e.vanilla.domain.Env;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateServiceDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @NotEmpty(message = "服务名称不能为空")
    private String serviceName;
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
     * 更新策略 PollingUpdate
     */
    private String strategy;

    private List<Env> envs;

}
