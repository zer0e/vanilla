package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.application.dto.LoginDto;
import com.github.zer0e.vanilla.application.vo.LoginVo;
import com.github.zer0e.vanilla.application.vo.UserInfoVo;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证：登录（公开）换取 JWT；/me 需携带 JWT
 */
@RestController
@Tag(name = "认证")
@RequestMapping("/auth/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/v1/login")
    @Operation(summary = "登录，返回 JWT")
    public RestResponse<LoginVo> login(@RequestBody @Valid LoginDto loginDto) throws BusinessException {
        return RestResponse.ok(userService.login(loginDto));
    }

    @GetMapping("/v1/me")
    @Operation(summary = "当前登录用户信息")
    public RestResponse<UserInfoVo> me() throws BusinessException {
        return RestResponse.ok(userService.me());
    }
}