package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The type Role permission do.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@TableName("t_role_permission")
public class RolePermissionDo {
    /**
     * The id.
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * The Role id.
     */
    private Integer roleId;

    /**
     * The Permission id.
     */
    private Integer permissionId;
}
