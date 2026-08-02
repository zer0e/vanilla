package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginDto {

    @Schema(description = "登录名")
    @NotBlank(message = "登录名不能为空")
    private String loginName;

    @Schema(description = "密码")
    @NotBlank(message = "密码不能为空")
    private String password;
}