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
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("t_user_role")
public class UserRoleDo extends Base {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer roleId;
    private Integer userId;
    private Integer stackId;
    private Integer clusterId;
}
