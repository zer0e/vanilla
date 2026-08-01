package com.github.zer0e.vanilla.application.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.StackService;
import com.github.zer0e.vanilla.application.UserService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.CreateStackDto;
import com.github.zer0e.vanilla.application.dto.GetStacksDto;
import com.github.zer0e.vanilla.application.dto.UpdateStackDto;
import com.github.zer0e.vanilla.application.vo.StackVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.domain.StackRole;
import com.github.zer0e.vanilla.domain.User;
import com.github.zer0e.vanilla.infrastructure.converter.StackConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.UserRoleMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class StackServiceImpl implements StackService {
    private final StackMapper stackMapper;
    private final UserService userService;
    private final UserRoleMapper userRoleMapper;
    private final HistoryService historyService;
    /**
     * Create stack stack vo.
     *
     * @param createStackDto the creation stack dto
     * @return the stack vo
     * @throws BusinessException the business exception
     */
    @Override
    @PreAuthorize("hasAnyRole('cluster_' + #createStackDto.clusterId + '_cluster_admin'," +
            "'cluster_' + #createStackDto.clusterId + '_cluster_user')")
    @Transactional(rollbackFor = Exception.class)
    public StackVo createStack(CreateStackDto createStackDto) throws BusinessException {
        StackDo stackDo = StackConverter.INSTANCE.toDo(createStackDto);
        stackDo.setCreateTime(LocalDateTime.now());
        String currentUserName = SecurityUtil.getCurrentUserName();
        Integer userId = SecurityUtil.getCurrentUserId();

        // 检查同一集群下是否有重名
        StackDo repeat = stackMapper.selectByClusterIdAndName(stackDo.getClusterId(), stackDo.getStackName());
        if (repeat != null) {
            throw new BusinessException(Constants.STACK_DUPLICATE);
        }

        stackDo.setCreateUser(currentUserName);
        stackDo.setStatus(DataStatus.EXIST.ordinal());
        stackDo.setOwner(currentUserName);
        stackMapper.insert(stackDo);

        RoleDo stackAdminRole = userService.getRoleByName(StackRole.STACK_ADMIN.name().toLowerCase());
        Assert.notNull(stackAdminRole, Constants.ROLE_NOT_EXIST);

        UserRoleDo userRoleDo = UserRoleDo.builder()
                .userId(userId)
                .roleId(stackAdminRole.getId())
                .stackId(stackDo.getId())
                .createUser(currentUserName)
                .createTime(LocalDateTime.now())
                .build();

        userRoleMapper.insert(userRoleDo);
        recordHistory(stackDo.getId(), "创建栈 " + stackDo.getStackName());

        return StackConverter.INSTANCE.toVo(stackDo);
    }

    /**
     * Update stack stack vo.
     *
     * @param updateStackDto the update stack dto
     * @return the stack vo
     * @throws BusinessException the business exception
     */
    @Override
    @PreAuthorize("hasAnyRole('stack_' + #updateStackDto?.id + '_stack_admin')")
    @Transactional(rollbackFor = Exception.class)
    public StackVo updateStack(UpdateStackDto updateStackDto) throws BusinessException {
        Integer id = updateStackDto.getId();
        StackDo stackDo = stackMapper.selectById(id);
        if (stackDo == null || stackDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        // 改名时校验同集群下不与其他存活栈重名
        if (StringUtils.hasText(updateStackDto.getStackName())
                && !Objects.equals(updateStackDto.getStackName(), stackDo.getStackName())) {
            StackDo repeat = stackMapper.selectByClusterIdAndName(stackDo.getClusterId(), updateStackDto.getStackName());
            if (repeat != null) {
                throw new BusinessException(Constants.STACK_DUPLICATE);
            }
        }
        BeanUtils.copyProperties(updateStackDto, stackDo);
        stackDo.setModifyTime(LocalDateTime.now());
        stackDo.setModifyUser(SecurityUtil.getCurrentUserName());
        stackMapper.updateById(stackDo);
        recordHistory(stackDo.getId(), "更新栈 " + stackDo.getStackName());
        return StackConverter.INSTANCE.toVo(stackDo);
    }

    /**
     * Delete stack.
     *
     * @param updateStackDto the update stack dto
     * @throws BusinessException the business exception
     */
    @Override
    @PreAuthorize("hasAnyRole('stack_' + #updateStackDto?.id + '_stack_admin')")
    public void deleteStack(UpdateStackDto updateStackDto) throws BusinessException {
        Integer id = updateStackDto.getId();
        StackDo stackDo = stackMapper.selectById(id);
        if (stackDo == null || stackDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        stackDo.setStatus(DataStatus.NOT_EXIST.ordinal());
        stackDo.setDeleteTime(LocalDateTime.now());
        stackDo.setDeleteUser(SecurityUtil.getCurrentUserName());
        stackMapper.updateById(stackDo);
        recordHistory(stackDo.getId(), "删除栈 " + stackDo.getStackName());

    }

    /**
     * 获取当前用户有权限的栈
     *
     * @param getStacksDto the get stacks dto
     * @return the stacks
     * @throws BusinessException the business exception
     */
    @Override
    @PreAuthorize("hasAnyRole('cluster_' + #getStacksDto.clusterId + '_cluster_admin'," +
            "'cluster_' + #getStacksDto.clusterId + '_cluster_user')")
    public PageData<StackVo> getStacks(GetStacksDto getStacksDto) throws BusinessException {
        Integer clusterId = getStacksDto.getClusterId();
        Integer size = getStacksDto.getSize();
        Integer page = getStacksDto.getPage();
        String search = getStacksDto.getSearch();
        User currentUser = SecurityUtil.getCurrentUser();
        assert currentUser != null;
        List<Integer> stackIds = userRoleMapper.selectHasPermissionStackIdsByUserId(currentUser.getId());
        PageHelper.startPage(page, size);
        List<StackDo> stackDos = stackMapper.selectStacksByClusterIdAndStackIds(clusterId, stackIds, search);
        List<StackVo> stackVos = stackDos.stream().map(StackConverter.INSTANCE::toVo).toList();
        PageInfo<StackDo> pageInfo = new PageInfo<>(stackDos);
        return new PageData<>(page, size, pageInfo.getTotal(), stackVos);
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}
