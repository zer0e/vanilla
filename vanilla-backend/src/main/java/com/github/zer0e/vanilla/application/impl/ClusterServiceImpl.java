package com.github.zer0e.vanilla.application.impl;

import com.github.zer0e.vanilla.application.ClusterService;
import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.application.dto.CreateClusterDto;
import com.github.zer0e.vanilla.application.dto.UpdateClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.StringConstant;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.ClusterRole;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.domain.User;
import com.github.zer0e.vanilla.infrastructure.converter.ClusterConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ClusterMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.UserRoleMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterServiceImpl implements ClusterService {

    private final ClusterMapper clusterMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserService userService;
    private final RedissonClient redissonClient;

    @Override
    @PreAuthorize("hasRole('admin')")
    @Transactional
    public ClusterVo createCluster(CreateClusterDto createClusterDto) throws BusinessException {
        ClusterDo clusterDo = ClusterConverter.INSTANCE.toDo(createClusterDto);
        User currentUser = SecurityUtil.getCurrentUser();
        Assert.notNull(currentUser, StringConstant.USER_INFO_NOT_EXIST);
        clusterDo.setCreateUser(currentUser.getLoginName());
        clusterDo.setCreateTime(LocalDateTime.now());
        clusterMapper.insert(clusterDo);
        Integer userId = SecurityUtil.getCurrentUserId();
        RoleDo clusterAdminRole = userService.getRoleByName(ClusterRole.CLUSTER_ADMIN.name().toLowerCase());
        if (clusterAdminRole == null) {
            throw new BusinessException(StringConstant.ROLE_NOT_EXIST);
        }
        RoleDo clusterUserRole = userService.getRoleByName(ClusterRole.CLUSTER_USER.name().toLowerCase());
        if (clusterUserRole == null) {
            throw new BusinessException(StringConstant.ROLE_NOT_EXIST);
        }
        UserRoleDo userRoleDo = UserRoleDo.builder()
                .userId(userId)
                .roleId(clusterAdminRole.getId())
                .clusterId(clusterDo.getId())
                .createUser(currentUser.getLoginName())
                .createTime(LocalDateTime.now())
                .build();

        List<UserRoleDo> users = new ArrayList<>();
        users.add(userRoleDo);

        // 普通成员
        List<Integer> userIds = createClusterDto.getUserIds();
        if (!CollectionUtils.isEmpty(userIds)) {
            users.addAll(userIds.stream().map(id -> UserRoleDo.builder()
                    .userId(id)
                    .roleId(clusterUserRole.getId())
                    .clusterId(clusterDo.getId())
                    .createUser(currentUser.getLoginName())
                    .createTime(LocalDateTime.now())
                    .build()).toList());
        }
        userRoleMapper.insert(users);

        return ClusterConverter.INSTANCE.toVo(clusterDo);
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    @Transactional
    public ClusterVo updateCluster(UpdateClusterDto updateClusterDto) throws BusinessException {
        ClusterDo clusterDo = clusterMapper.selectById(updateClusterDto.getId());
        if (clusterDo == null || clusterDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(StringConstant.CLUSTER_NOT_EXIST);
        }
        User currentUser = SecurityUtil.getCurrentUser();
        Assert.notNull(currentUser, StringConstant.USER_INFO_NOT_EXIST);
        BeanUtils.copyProperties(updateClusterDto, clusterDo);
        clusterDo.setModifyTime(LocalDateTime.now());
        clusterDo.setModifyUser(currentUser.getLoginName());
        clusterMapper.updateById(clusterDo);

        List<Integer> userIds = updateClusterDto.getUserIds();
        RLock lock = redissonClient.getLock(StringConstant.LOCK_PREFIX + "cluster-" + updateClusterDto.getId());
        try {
            lock.lock();
            updateClusterUsers(userIds, updateClusterDto.getId());
        }finally {
            lock.unlock();
        }
        return ClusterConverter.INSTANCE.toVo(clusterDo);
    }

    @Override
    @PreAuthorize("hasRole('admin')")
    public void deleteCluster(Integer id) throws BusinessException {
        ClusterDo clusterDo = clusterMapper.selectById(id);
        if (clusterDo == null || clusterDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(StringConstant.CLUSTER_NOT_EXIST);
        }
        User currentUser = SecurityUtil.getCurrentUser();
        Assert.notNull(currentUser, StringConstant.USER_INFO_NOT_EXIST);
        clusterDo.setStatus(DataStatus.NOT_EXIST.ordinal());
        clusterDo.setDeleteTime(LocalDateTime.now());
        clusterDo.setDeleteUser(currentUser.getLoginName());
        clusterMapper.updateById(clusterDo);
    }

    @Override
    public List<ClusterVo> getClusters() throws BusinessException {
        Integer userId = SecurityUtil.getCurrentUserId();
        List<UserRoleDo> clusterUserRoles = userService.getClusterUserRoles(userId);
        if (CollectionUtils.isEmpty(clusterUserRoles)) {
            return Collections.emptyList();
        }
        List<Integer> clusterIds = clusterUserRoles.stream().map(UserRoleDo::getClusterId).toList();
        List<ClusterDo> clusterDos = clusterMapper.selectBatchIds(clusterIds);
        if (!CollectionUtils.isEmpty(clusterDos)) {
            return clusterDos.stream()
                    .filter(clusterDo -> clusterDo.getStatus() == DataStatus.EXIST.ordinal())
                    .map(ClusterConverter.INSTANCE::toVo).toList();
        }
        return Collections.emptyList();
    }

    @Override
    public List<UserRoleDo> getClusterUsers(Integer clusterId) {
        return userRoleMapper.selectUserRolesByClusterId(clusterId);
    }

    /**
     * 更新集群人员，需外围加锁
     *
     * @param userIds   the user ids
     * @param clusterId the cluster id
     */
    private void updateClusterUsers(Collection<Integer> userIds, Integer clusterId) {
        if (userIds == null) {
            userIds = Collections.emptyList();
        }
        RoleDo clusterAdminRole = userService.getRoleByName(ClusterRole.CLUSTER_ADMIN.name().toLowerCase());

        List<UserRoleDo> clusterUsers = this.getClusterUsers(clusterId);
        Set<Integer> existUserIds = clusterUsers.stream()
                .filter(userRoleDo -> !Objects.equals(userRoleDo.getRoleId(), clusterAdminRole.getId()))
                .map(UserRoleDo::getUserId).collect(Collectors.toSet());
        List<Integer> deleteIds = new ArrayList<>();
        Set<Integer> stillExistUserIds = new HashSet<>();
        List<UserRoleDo> insertUsers = new ArrayList<>();
        RoleDo clusterUserRole = userService.getRoleByName(ClusterRole.CLUSTER_USER.name().toLowerCase());

        for (Integer userId : userIds) {
            if (!existUserIds.contains(userId)) {
                insertUsers.add(UserRoleDo.builder()
                        .userId(userId)
                        .roleId(clusterUserRole.getId())
                        .createTime(LocalDateTime.now())
                        .createUser(SecurityUtil.getCurrentUserName())
                        .clusterId(clusterId)
                        .build());
            }else {
                stillExistUserIds.add(userId);
            }
        }

        for (UserRoleDo clusterUser : clusterUsers) {
            if (!Objects.equals(clusterUser.getRoleId(), clusterAdminRole.getId())
                    && !stillExistUserIds.contains(clusterUser.getUserId())) {
                deleteIds.add(clusterUser.getId());
            }
        }
        if (!CollectionUtils.isEmpty(deleteIds)) {
            userRoleMapper.deleteByIds(deleteIds);
        }
        if (!CollectionUtils.isEmpty(insertUsers)) {
            userRoleMapper.insert(insertUsers);
        }
    }
}
