package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserDto {

    @NotEmpty(message = "昵称不能为空")
    private String nikeName;

    @NotEmpty(message = "登录名不能为空")
    private String loginName;

    /**
     * 状态 0正常 1禁用，默认 0
     */
    private Integer status;

    /**
     * 初始角色绑定，可选
     */
    private List<RoleBindingDto> roles;
}
