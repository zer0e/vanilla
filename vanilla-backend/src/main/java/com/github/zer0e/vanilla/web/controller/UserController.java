package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.application.dto.CreateUserDto;
import com.github.zer0e.vanilla.application.dto.DeleteUserDto;
import com.github.zer0e.vanilla.application.dto.GetUsersDto;
import com.github.zer0e.vanilla.application.dto.UpdateUserDto;
import com.github.zer0e.vanilla.application.vo.UserVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（仅 admin 可操作）
 */
@RestController
@Tag(name = "用户相关")
@RequestMapping("/user/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/v1/create")
    @Operation(summary = "创建用户")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<UserVo> createUser(@RequestBody @Valid CreateUserDto createUserDto) throws BusinessException {
        return RestResponse.ok(userService.createUser(createUserDto));
    }

    @PostMapping("/v1/update")
    @Operation(summary = "修改用户")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<UserVo> updateUser(@RequestBody @Valid UpdateUserDto updateUserDto) throws BusinessException {
        return RestResponse.ok(userService.updateUser(updateUserDto));
    }

    @PostMapping("/v1/delete")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<Void> deleteUser(@RequestBody @Valid DeleteUserDto deleteUserDto) throws BusinessException {
        userService.deleteUser(deleteUserDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/list")
    @Operation(summary = "用户列表")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<PageData<UserVo>> getUsers(@RequestBody @Valid GetUsersDto getUsersDto) throws BusinessException {
        return RestResponse.ok(userService.getUsers(getUsersDto));
    }
}
