package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateUserDto;
import com.github.zer0e.vanilla.application.dto.DeleteUserDto;
import com.github.zer0e.vanilla.application.dto.GetUsersDto;
import com.github.zer0e.vanilla.application.dto.UpdateUserDto;
import com.github.zer0e.vanilla.application.vo.UserVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;

import java.util.List;

public interface UserService {

    List<UserRoleDo> getClusterUserRoles(Integer userId);

    RoleDo getRoleByName(String roleName);

    PageData<UserVo> getUsers(GetUsersDto getUsersDto) throws BusinessException;

    UserVo createUser(CreateUserDto createUserDto) throws BusinessException;

    UserVo updateUser(UpdateUserDto updateUserDto) throws BusinessException;

    void deleteUser(DeleteUserDto deleteUserDto) throws BusinessException;

    /**
     * 使指定用户的 Redis 权限缓存立即失效（新授予角色后调用，避免最长 24h 延迟生效）
     */
    void evictUserCache(String loginName);
}
