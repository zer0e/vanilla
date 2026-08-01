package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.github.zer0e.vanilla.domain.Env;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_service", autoResultMap = true)
public class ServiceDo extends Base {
    @TableId(type = IdType.AUTO)
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
     * 更新策略 RollingUpdate
     */
    private String strategy;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Env> envs;
}
