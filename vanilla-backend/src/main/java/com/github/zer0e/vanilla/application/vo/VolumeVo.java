package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

@Data
public class VolumeVo {
    private Integer id;

    private Integer stackId;

    /**
     * 卷名称 一旦创建不允许修改
     */
    private String volumeName;

    /**
     * 卷大小 单位GB
     */
    private Integer size;
}
