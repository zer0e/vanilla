package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_volume")
public class VolumeDo extends Base {
    @TableId(type = IdType.AUTO)
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
