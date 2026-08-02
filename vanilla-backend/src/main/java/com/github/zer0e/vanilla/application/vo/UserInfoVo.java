package com.github.zer0e.vanilla.application.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户信息（含是否全局管理员，供前端按角色渲染入口）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoVo {
    private String loginName;
    private String nikeName;
    private Boolean isAdmin;
}