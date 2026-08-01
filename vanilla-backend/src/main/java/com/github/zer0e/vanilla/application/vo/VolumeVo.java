package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

@Data
public class VolumeVo {
    private Integer id;

    private Integer stackId;

    /**
     * 所属服务
     */
    private Integer serviceId;

    /**
     * 卷名称 一旦创建不允许修改
     */
    private String volumeName;

    /**
     * 卷大小 单位GB
     */
    private Integer size;

    /**
     * 容器内挂载路径
     */
    private String mountPath;
}
