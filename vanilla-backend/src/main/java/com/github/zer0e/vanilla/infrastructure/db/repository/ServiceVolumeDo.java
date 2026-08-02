package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 服务引用卷（一个卷可被多个服务引用；删除服务不影响卷）
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_service_volume")
public class ServiceVolumeDo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer serviceId;

    private Integer volumeId;

    private LocalDateTime createTime;
}