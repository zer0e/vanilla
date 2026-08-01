package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

/**
 * 用户角色绑定（含作用域）
 */
@Data
public class UserRoleBindingVo {
    private Integer roleId;
    private String roleName;
    private Integer clusterId;
    private Integer stackId;
}
