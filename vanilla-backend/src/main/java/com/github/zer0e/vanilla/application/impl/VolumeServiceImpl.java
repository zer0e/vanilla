package com.github.zer0e.vanilla.application.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.VolumeService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.CreateVolumeDto;
import com.github.zer0e.vanilla.application.dto.DeleteVolumeDto;
import com.github.zer0e.vanilla.application.dto.GetVolumesDto;
import com.github.zer0e.vanilla.application.dto.UpdateVolumeDto;
import com.github.zer0e.vanilla.application.vo.VolumeVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.infrastructure.converter.VolumeConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.VolumeMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class VolumeServiceImpl implements VolumeService {

    private final VolumeMapper volumeMapper;
    private final StackMapper stackMapper;
    private final ServiceMapper serviceMapper;
    private final HistoryService historyService;

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #createVolumeDto.stackId + '_stack_admin'," +
            "'stack_' + #createVolumeDto.stackId + '_stack_member')")
    @Transactional(rollbackFor = Exception.class)
    public VolumeVo createVolume(CreateVolumeDto createVolumeDto) throws BusinessException {
        ServiceDo serviceDo = checkService(createVolumeDto.getStackId(), createVolumeDto.getServiceId());
        VolumeDo repeat = volumeMapper.selectByServiceIdAndName(createVolumeDto.getServiceId(), createVolumeDto.getVolumeName());
        if (repeat != null) {
            throw new BusinessException(Constants.VOLUME_DUPLICATE);
        }
        VolumeDo volumeDo = VolumeConverter.INSTANCE.toDo(createVolumeDto);
        String currentUserName = SecurityUtil.getCurrentUserName();
        volumeDo.setStatus(DataStatus.EXIST.ordinal());
        volumeDo.setCreateUser(currentUserName);
        volumeDo.setCreateTime(LocalDateTime.now());
        volumeMapper.insert(volumeDo);
        recordHistory(createVolumeDto.getStackId(),
                "为服务 " + serviceDo.getServiceName() + " 创建卷 " + volumeDo.getVolumeName()
                        + "（" + volumeDo.getSize() + "GB → " + volumeDo.getMountPath() + "）");
        return VolumeConverter.INSTANCE.toVo(volumeDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #updateVolumeDto.stackId + '_stack_admin')")
    @Transactional(rollbackFor = Exception.class)
    public VolumeVo updateVolume(UpdateVolumeDto updateVolumeDto) throws BusinessException {
        VolumeDo volumeDo = volumeMapper.selectById(updateVolumeDto.getId());
        if (volumeDo == null || volumeDo.getStatus() != DataStatus.EXIST.ordinal()
                || !Objects.equals(volumeDo.getStackId(), updateVolumeDto.getStackId())) {
            throw new BusinessException(Constants.VOLUME_NOT_EXIST);
        }
        // 卷名称创建后不允许修改，可更新大小与挂载路径
        if (updateVolumeDto.getSize() != null) {
            volumeDo.setSize(updateVolumeDto.getSize());
        }
        if (updateVolumeDto.getMountPath() != null) {
            volumeDo.setMountPath(updateVolumeDto.getMountPath());
        }
        volumeDo.setModifyTime(LocalDateTime.now());
        volumeDo.setModifyUser(SecurityUtil.getCurrentUserName());
        volumeMapper.updateById(volumeDo);
        recordHistory(updateVolumeDto.getStackId(), "更新卷 " + volumeDo.getVolumeName() + "（" + volumeDo.getSize() + "GB → " + volumeDo.getMountPath() + "）");
        return VolumeConverter.INSTANCE.toVo(volumeDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deleteVolumeDto.stackId + '_stack_admin')")
    public void deleteVolume(DeleteVolumeDto deleteVolumeDto) throws BusinessException {
        VolumeDo volumeDo = volumeMapper.selectById(deleteVolumeDto.getId());
        if (volumeDo == null || volumeDo.getStatus() != DataStatus.EXIST.ordinal()
                || !Objects.equals(volumeDo.getStackId(), deleteVolumeDto.getStackId())) {
            throw new BusinessException(Constants.VOLUME_NOT_EXIST);
        }
        volumeDo.setStatus(DataStatus.NOT_EXIST.ordinal());
        volumeDo.setDeleteTime(LocalDateTime.now());
        volumeDo.setDeleteUser(SecurityUtil.getCurrentUserName());
        volumeMapper.updateById(volumeDo);
        recordHistory(deleteVolumeDto.getStackId(), "删除卷 " + volumeDo.getVolumeName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #getVolumesDto.stackId + '_stack_admin'," +
            "'stack_' + #getVolumesDto.stackId + '_stack_member'," +
            "'stack_' + #getVolumesDto.stackId + '_stack_readonly')")
    public PageData<VolumeVo> getVolumes(GetVolumesDto getVolumesDto) throws BusinessException {
        Integer size = getVolumesDto.getSize();
        Integer page = getVolumesDto.getPage();
        String search = getVolumesDto.getSearch();
        PageHelper.startPage(page, size);
        List<VolumeDo> volumeDos = volumeMapper.selectVolumesByServiceIdAndSearch(getVolumesDto.getServiceId(), search);
        List<VolumeVo> volumeVos = volumeDos.stream().map(VolumeConverter.INSTANCE::toVo).toList();
        PageInfo<VolumeDo> pageInfo = new PageInfo<>(volumeDos);
        return new PageData<>(page, size, pageInfo.getTotal(), volumeVos);
    }

    private ServiceDo checkService(Integer stackId, Integer serviceId) throws BusinessException {
        StackDo stackDo = stackMapper.selectById(stackId);
        if (stackDo == null || stackDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        ServiceDo serviceDo = serviceMapper.selectById(serviceId);
        if (serviceDo == null || serviceDo.getStatus() != DataStatus.EXIST.ordinal()
                || !Objects.equals(serviceDo.getStackId(), stackId)) {
            throw new BusinessException(Constants.SERVICE_NOT_EXIST);
        }
        return serviceDo;
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}
