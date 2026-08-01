package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateUserDto {

    @NotNull(message = "用户id不能为空")
    private Integer id;

    private String nikeName;

    /**
     * 状态 0正常 1禁用
     */
    private Integer status;

    /**
     * 角色绑定：null 表示不修改，非 null 表示全量替换
     */
    private List<RoleBindingDto> roles;
}
