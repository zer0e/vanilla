package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Volume {

    private Integer id;
    private Integer stackId;
    /**
     * 卷大小 单位GB
     */
    private Integer size;
    /**
     * 卷名称 一旦创建不允许修改
     */
    private String volumeName;

}
