package com.github.zer0e.vanilla.application.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回：JWT 与用户基本信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginVo {
    private String token;
    private String loginName;
    private String nikeName;
}