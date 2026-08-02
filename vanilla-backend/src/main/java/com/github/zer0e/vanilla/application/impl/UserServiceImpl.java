package com.github.zer0e.vanilla.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.application.config.security.JwtTokenProvider;
import com.github.zer0e.vanilla.application.dto.CreateUserDto;
import com.github.zer0e.vanilla.application.dto.DeleteUserDto;
import com.github.zer0e.vanilla.application.dto.GetUsersDto;
import com.github.zer0e.vanilla.application.dto.LoginDto;
import com.github.zer0e.vanilla.application.dto.RoleBindingDto;
import com.github.zer0e.vanilla.application.dto.UpdateUserDto;
import com.github.zer0e.vanilla.application.vo.LoginVo;
import com.github.zer0e.vanilla.application.vo.UserInfoVo;
import com.github.zer0e.vanilla.application.vo.UserRoleBindingVo;
import com.github.zer0e.vanilla.application.vo.UserVo;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.User;
import com.github.zer0e.vanilla.domain.UserRolePermission;
import com.github.zer0e.vanilla.infrastructure.converter.UserConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.*;
import com.github.zer0e.vanilla.infrastructure.db.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService, UserDetailsService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Set<String> CLUSTER_ROLES = Set.of("cluster_admin", "cluster_user");
    private static final Set<String> STACK_ROLES = Set.of("stack_admin", "stack_member", "stack_readonly");

    @Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        String cacheKey = Constants.USER_CACHE_PREFIX + username;
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
        List<UserRoleDo> userRoles = userRoleMapper.selectUserRolesByUserId(userDo.getId());
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
                Integer clusterId = userRole.getClusterId();
                Integer stackId = userRole.getStackId();

                String roleName = roleIdAndNameMap.get(roleId);
                List<String> permissions = roleIdAndPermissionMap.get(roleId);

                authorities.add(new UserRolePermission(clusterId, stackId,
                        true, roleName));

                if (!CollectionUtils.isEmpty(permissions)) {
                    for (String permission : permissions) {
                        authorities.add(new UserRolePermission(clusterId, stackId,
                                        false, permission));
                    }
                }

            }
        }

        User user = UserConverter.INSTANCE.toUser(userDo);
        user.setAuthorities(authorities);
        //set user cache
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(user),
                    Constants.NUM_24,
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
        List<RolePermissionDo> rolePermissionDos = rolePermissionMapper.selectRolePermissionByRoleIds(roleIds);
        // 权限id和名称对应关系
        Map<Integer, String> permissionMap = new HashMap<>();
        // 角色id和权限的对应关系
        Map<Integer, List<String>> rolePermissionMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(rolePermissionDos)) {
            List<Integer> permissionIds = rolePermissionDos.stream().map(RolePermissionDo::getPermissionId).collect(Collectors.toList());
            List<PermissionDo> permissionDos = permissionMapper.selectPermissionByIds(permissionIds);
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

    @Override
    public LoginVo login(LoginDto loginDto) throws BusinessException {
        // 不按状态过滤（findByLoginName 自带 status=0），以便区分「不存在 / 已禁用 / 密码错误」
        UserDo user = userMapper.selectOne(new LambdaQueryWrapper<UserDo>()
                .eq(UserDo::getLoginName, loginDto.getLoginName()));
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException("账号已禁用，请联系管理员");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("账号未设置密码，请联系管理员");
        }
        if (!passwordEncoder.matches(loginDto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getLoginName());
        return LoginVo.builder()
                .token(token)
                .loginName(user.getLoginName())
                .nikeName(user.getNikeName())
                .build();
    }

    @Override
    public UserInfoVo me() throws BusinessException {
        User user = SecurityUtil.getCurrentUser();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        boolean admin = user.getAuthorities() != null && user.getAuthorities().stream()
                .anyMatch(p -> (Constants.ROLE_PREFIX + "admin").equals(p.getAuthority()));
        return UserInfoVo.builder()
                .loginName(user.getLoginName())
                .nikeName(user.getNikeName())
                .isAdmin(admin)
                .build();
    }

    @Override
    public List<UserRoleDo> getClusterUserRoles(Integer userId) {
        LambdaQueryWrapper<UserRoleDo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserRoleDo::getUserId, userId)
                .isNotNull(UserRoleDo::getClusterId);
        return userRoleMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    @Cacheable(cacheNames = "roles", key = "'role_' + #roleName", unless = "#result==null")
    public RoleDo getRoleByName(String roleName) {
        return roleMapper.selectOne(new LambdaQueryWrapper<RoleDo>().eq(RoleDo::getRoleName, roleName));
    }

    @Override
    public PageData<UserVo> getUsers(GetUsersDto getUsersDto) throws BusinessException {
        Integer page = getUsersDto.getPage() == null ? 1 : getUsersDto.getPage();
        Integer size = getUsersDto.getSize() == null ? 15 : getUsersDto.getSize();
        PageHelper.startPage(page, size);
        List<UserDo> userDos = userMapper.selectUsersBySearch(getUsersDto.getSearch());
        List<UserVo> userVos = userDos.stream().map(this::toUserVo).toList();
        PageInfo<UserDo> pageInfo = new PageInfo<>(userDos);
        return new PageData<>(page, size, pageInfo.getTotal(), userVos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVo createUser(CreateUserDto createUserDto) throws BusinessException {
        UserDo exist = userMapper.selectOne(new LambdaQueryWrapper<UserDo>()
                .eq(UserDo::getLoginName, createUserDto.getLoginName()));
        if (exist != null) {
            throw new BusinessException(Constants.USER_EXIST);
        }
        UserDo userDo = new UserDo();
        userDo.setNikeName(createUserDto.getNikeName());
        userDo.setLoginName(createUserDto.getLoginName());
        if (StringUtils.hasText(createUserDto.getPassword())) {
            userDo.setPassword(passwordEncoder.encode(createUserDto.getPassword()));
        }
        userDo.setStatus(createUserDto.getStatus() == null ? 0 : createUserDto.getStatus());
        userDo.setCreateTime(LocalDateTime.now());
        userMapper.insert(userDo);
        if (!CollectionUtils.isEmpty(createUserDto.getRoles())) {
            replaceRoles(userDo, createUserDto.getRoles());
        }
        return toUserVo(userDo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVo updateUser(UpdateUserDto updateUserDto) throws BusinessException {
        UserDo userDo = userMapper.selectById(updateUserDto.getId());
        if (userDo == null) {
            throw new BusinessException(Constants.USER_NOT_EXIST);
        }
        if (updateUserDto.getNikeName() != null) {
            userDo.setNikeName(updateUserDto.getNikeName());
        }
        if (StringUtils.hasText(updateUserDto.getPassword())) {
            userDo.setPassword(passwordEncoder.encode(updateUserDto.getPassword()));
        }
        if (updateUserDto.getStatus() != null) {
            userDo.setStatus(updateUserDto.getStatus());
        }
        userMapper.updateById(userDo);
        if (updateUserDto.getRoles() != null) {
            replaceRoles(userDo, updateUserDto.getRoles());
        }
        evictUserCache(userDo.getLoginName());
        return toUserVo(userDo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(DeleteUserDto deleteUserDto) throws BusinessException {
        UserDo userDo = userMapper.selectById(deleteUserDto.getId());
        if (userDo == null) {
            throw new BusinessException(Constants.USER_NOT_EXIST);
        }
        // 禁用 + 清除角色绑定，使账号失效
        userDo.setStatus(1);
        userMapper.updateById(userDo);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDo>().eq(UserRoleDo::getUserId, userDo.getId()));
        evictUserCache(userDo.getLoginName());
    }

    private UserVo toUserVo(UserDo userDo) {
        UserVo vo = new UserVo();
        vo.setId(userDo.getId());
        vo.setNikeName(userDo.getNikeName());
        vo.setLoginName(userDo.getLoginName());
        vo.setStatus(userDo.getStatus());
        vo.setCreateTime(userDo.getCreateTime());
        vo.setRoles(getUserRoleBindings(userDo.getId()));
        return vo;
    }

    private List<UserRoleBindingVo> getUserRoleBindings(Integer userId) {
        List<UserRoleDo> userRoles = userRoleMapper.selectUserRolesByUserId(userId);
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptyList();
        }
        Map<Integer, String> roleNames = getRoleMap(userRoles.stream().map(UserRoleDo::getRoleId).toList());
        return userRoles.stream().map(userRole -> {
            UserRoleBindingVo binding = new UserRoleBindingVo();
            binding.setRoleId(userRole.getRoleId());
            binding.setRoleName(roleNames.get(userRole.getRoleId()));
            binding.setClusterId(userRole.getClusterId());
            binding.setStackId(userRole.getStackId());
            return binding;
        }).toList();
    }

    /**
     * 全量替换用户角色绑定（删除旧绑定后写入新绑定）
     */
    private void replaceRoles(UserDo user, List<RoleBindingDto> bindings) throws BusinessException {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleDo>().eq(UserRoleDo::getUserId, user.getId()));
        List<UserRoleDo> userRoles = new ArrayList<>();
        for (RoleBindingDto binding : bindings) {
            RoleDo role = getRoleByName(binding.getRoleName());
            if (role == null) {
                throw new BusinessException(Constants.ROLE_NOT_EXIST);
            }
            if (CLUSTER_ROLES.contains(binding.getRoleName()) && binding.getClusterId() == null) {
                throw new BusinessException(Constants.CLUSTER_ROLE_NEED_CLUSTER);
            }
            if (STACK_ROLES.contains(binding.getRoleName()) && binding.getStackId() == null) {
                throw new BusinessException(Constants.STACK_ROLE_NEED_STACK);
            }
            userRoles.add(UserRoleDo.builder()
                    .userId(user.getId())
                    .roleId(role.getId())
                    .clusterId(binding.getClusterId())
                    .stackId(binding.getStackId())
                    .createUser(SecurityUtil.getCurrentUserName())
                    .createTime(LocalDateTime.now())
                    .build());
        }
        if (!userRoles.isEmpty()) {
            userRoleMapper.insert(userRoles);
        }
    }

    @Override
    public void evictUserCache(String loginName) {
        try {
            redisTemplate.delete(Constants.USER_CACHE_PREFIX + loginName);
        } catch (Exception e) {
            log.warn("invalidate user cache err, loginName={}", loginName, e);
        }
    }
}
