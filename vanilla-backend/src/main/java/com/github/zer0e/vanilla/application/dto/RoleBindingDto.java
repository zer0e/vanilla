package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户角色绑定项。全局角色（admin/user）无需作用域；
 * 集群角色（cluster_admin/cluster_user）需 clusterId；
 * 栈角色（stack_admin/stack_member/stack_readonly）需 stackId
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleBindingDto {

    @NotEmpty(message = "角色名不能为空")
    private String roleName;

    private Integer clusterId;

    private Integer stackId;
}
