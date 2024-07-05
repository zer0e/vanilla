package com.github.zer0e.vanilla.application.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.common.NumConstant;
import com.github.zer0e.vanilla.common.StringConstant;
import com.github.zer0e.vanilla.domain.User;
import com.github.zer0e.vanilla.domain.UserRolePermission;
import com.github.zer0e.vanilla.infrastructure.converter.UserConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.RoleMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.UserMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService, UserDetailsService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        String cacheKey = StringConstant.USER_CACHE_PREFIX + username;
        try {
            String userInfoCache = redisTemplate.opsForValue().get(cacheKey);
            if (userInfoCache != null) {
                try {
                    return objectMapper.readValue(userInfoCache, User.class);
                } catch (Exception e) {
                    log.warn("user cache to obj err: ", e);
                    redisTemplate.delete(cacheKey);
                }
            }
        }catch (Exception e) {
            log.warn("get user cache err", e);
        }
        UserDo userDo = userMapper.findByLoginName(username);
        if (userDo == null) {
            return null;
        }
        List<UserRoleDo> userRoles = roleMapper.selectRoleIdsByUserId(userDo.getId());
        List<UserRolePermission> authorities = new ArrayList<>();


        if (!CollectionUtils.isEmpty(userRoles)) {

            Map<Integer, String> roleIdAndNameMap = new HashMap<>();
            Map<Integer, List<String>> roleIdAndPermissionMap = new HashMap<>();

            List<Integer> roleIds = userRoles.stream().map(UserRoleDo::getRoleId).collect(Collectors.toList());

            if (!CollectionUtils.isEmpty(roleIds)) {
                roleIdAndNameMap = this.getRoleMap(roleIds);
                roleIdAndPermissionMap = this.getRolePermissionMap(roleIds);
            }

            for (UserRoleDo userRole : userRoles) {
                Integer roleId = userRole.getRoleId();
                Integer stackId = userRole.getStackId();

                String roleName = roleIdAndNameMap.get(roleId);
                List<String> permissions = roleIdAndPermissionMap.get(roleId);

                authorities.add(new UserRolePermission(stackId,
                        StringConstant.ROLE_PREFIX + roleName));

                if (!CollectionUtils.isEmpty(permissions)) {
                    for (String permission : permissions) {
                        authorities.add(new UserRolePermission(stackId,
                                        permission));
                    }
                }

            }
        }

        User user = UserConverter.INSTANCE.toUser(userDo);
        user.setAuthorities(authorities);
        //set user cache
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(user),
                    NumConstant.NUM_24,
                    TimeUnit.HOURS);
        }catch (Exception e) {
            log.warn("set user cache err", e);
        }
        return user;

    }

    /**
     * 获取角色id和角色名的关系
     *
     * @param ids the ids
     * @return the role map
     */
    private Map<Integer, String> getRoleMap(List<Integer> ids) {
        List<RoleDo> roleDos = roleMapper.selectRoleByIds(ids);
        Map<Integer, String> result = new HashMap<>();
        if (!CollectionUtils.isEmpty(roleDos)) {
            for (RoleDo roleDo : roleDos) {
                result.put(roleDo.getId(), roleDo.getRoleName());
            }
        }
        return result;
    }

    /**
     * 获取角色id和权限的对应关系
     *
     * @param roleIds the role ids
     * @return the role permission map
     */
    private Map<Integer, List<String>> getRolePermissionMap(List<Integer> roleIds) {
        List<RolePermissionDo> rolePermissionDos = roleMapper.selectRolePermissionByRoleIds(roleIds);
        // 权限id和名称对应关系
        Map<Integer, String> permissionMap = new HashMap<>();
        // 角色id和权限的对应关系
        Map<Integer, List<String>> rolePermissionMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(rolePermissionDos)) {
            List<Integer> permissionIds = rolePermissionDos.stream().map(RolePermissionDo::getPermissionId).collect(Collectors.toList());
            List<PermissionDo> permissionDos = roleMapper.selectPermissionByIds(permissionIds);
            if (!CollectionUtils.isEmpty(permissionDos)) {
                for (PermissionDo permissionDo : permissionDos) {
                    permissionMap.put(permissionDo.getId(), permissionDo.getPermissionName());
                }
            }
            for (RolePermissionDo rolePermissionDo : rolePermissionDos) {
                if (permissionMap.containsKey(rolePermissionDo.getPermissionId())) {
                    rolePermissionMap.computeIfAbsent(rolePermissionDo.getRoleId(), k -> new ArrayList<>())
                            .add(permissionMap.get(rolePermissionDo.getPermissionId()));
                }
            }

        }
        return rolePermissionMap;
    }
}
