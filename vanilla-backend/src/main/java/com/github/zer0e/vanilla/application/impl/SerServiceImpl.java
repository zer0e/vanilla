package com.github.zer0e.vanilla.application.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.SerService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.CreateServiceDto;
import com.github.zer0e.vanilla.application.dto.DeleteServiceDto;
import com.github.zer0e.vanilla.application.dto.GetServicesDto;
import com.github.zer0e.vanilla.application.dto.UpdateServiceDto;
import com.github.zer0e.vanilla.application.vo.ServiceVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.domain.Port;
import com.github.zer0e.vanilla.domain.Volume;
import com.github.zer0e.vanilla.infrastructure.converter.ServiceConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.VolumeMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class SerServiceImpl implements SerService {

    private final ServiceMapper serviceMapper;
    private final StackMapper stackMapper;
    private final PortMapper portMapper;
    private final VolumeMapper volumeMapper;
    private final HistoryService historyService;

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #createServiceDto.stackId + '_stack_admin'," +
            "'stack_' + #createServiceDto.stackId + '_stack_member')")
    @Transactional(rollbackFor = Exception.class)
    public ServiceVo createService(CreateServiceDto createServiceDto) throws BusinessException {
        StackDo stackDo = stackMapper.selectById(createServiceDto.getStackId());
        if (stackDo == null || stackDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.STACK_NOT_EXIST);
        }
        ServiceDo repeat = serviceMapper.selectByStackIdAndName(createServiceDto.getStackId(), createServiceDto.getServiceName());
        if (repeat != null) {
            throw new BusinessException(Constants.SERVICE_DUPLICATE);
        }
        ServiceDo serviceDo = ServiceConverter.INSTANCE.toDo(createServiceDto);
        String currentUserName = SecurityUtil.getCurrentUserName();
        serviceDo.setStatus(DataStatus.EXIST.ordinal());
        serviceDo.setCreateUser(currentUserName);
        serviceDo.setCreateTime(LocalDateTime.now());
        serviceMapper.insert(serviceDo);
        recordHistory(serviceDo.getStackId(), "创建服务 " + serviceDo.getServiceName() + "，镜像：" + serviceDo.getImage());
        return ServiceConverter.INSTANCE.toVo(serviceDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #updateServiceDto.stackId + '_stack_admin')")
    @Transactional(rollbackFor = Exception.class)
    public ServiceVo updateService(UpdateServiceDto updateServiceDto) throws BusinessException {
        ServiceDo serviceDo = serviceMapper.selectById(updateServiceDto.getId());
        if (serviceDo == null || serviceDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.SERVICE_NOT_EXIST);
        }
        if (!Objects.equals(updateServiceDto.getStackId(), serviceDo.getStackId())) {
            throw new BusinessException(Constants.SERVICE_NOT_EXIST);
        }
        BeanUtils.copyProperties(updateServiceDto, serviceDo);
        serviceDo.setModifyTime(LocalDateTime.now());
        serviceDo.setModifyUser(SecurityUtil.getCurrentUserName());
        serviceMapper.updateById(serviceDo);
        recordHistory(serviceDo.getStackId(), "更新服务 " + serviceDo.getServiceName());
        return ServiceConverter.INSTANCE.toVo(serviceDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deleteServiceDto.stackId + '_stack_admin')")
    public void deleteService(DeleteServiceDto deleteServiceDto) throws BusinessException {
        ServiceDo serviceDo = serviceMapper.selectById(deleteServiceDto.getServiceId());
        if (serviceDo == null || serviceDo.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.SERVICE_NOT_EXIST);
        }
        if (!Objects.equals(deleteServiceDto.getStackId(), serviceDo.getStackId())) {
            throw new BusinessException(Constants.SERVICE_NOT_EXIST);
        }
        serviceDo.setStatus(DataStatus.NOT_EXIST.ordinal());
        serviceDo.setDeleteTime(LocalDateTime.now());
        serviceDo.setDeleteUser(SecurityUtil.getCurrentUserName());
        serviceMapper.updateById(serviceDo);
        recordHistory(serviceDo.getStackId(), "删除服务 " + serviceDo.getServiceName());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #getServicesDto.stackId + '_stack_admin'," +
            "'stack_' + #getServicesDto.stackId + '_stack_member'," +
            "'stack_' + #getServicesDto.stackId + '_stack_readonly')")
    public PageData<ServiceVo> getServices(GetServicesDto getServicesDto) throws BusinessException {
        Integer stackId = getServicesDto.getStackId();
        Integer size = getServicesDto.getSize();
        Integer page = getServicesDto.getPage();
        String search = getServicesDto.getSearch();
        PageHelper.startPage(page, size);
        List<ServiceDo> serviceDos = serviceMapper.selectServicesByStackIdAndSearch(stackId, search);
        List<ServiceVo> serviceVos = serviceDos.stream().map(ServiceConverter.INSTANCE::toVo).toList();
        fillPortsAndVolumes(stackId, serviceVos);
        PageInfo<ServiceDo> pageInfo = new PageInfo<>(serviceDos);
        return new PageData<>(page, size, pageInfo.getTotal(), serviceVos);
    }

    /**
     * 为服务列表填充关联的端口和卷
     */
    private void fillPortsAndVolumes(Integer stackId, List<ServiceVo> serviceVos) {
        if (CollectionUtils.isEmpty(serviceVos)) {
            return;
        }
        List<Integer> serviceIds = serviceVos.stream().map(ServiceVo::getId).toList();
        List<PortDo> portDos = portMapper.selectPortsByServiceIds(serviceIds);
        Map<Integer, List<Port>> portMap = portDos.stream().collect(Collectors.groupingBy(PortDo::getServiceId,
                Collectors.mapping(SerServiceImpl::toPort, Collectors.toList())));
        List<Volume> volumes = volumeMapper.selectVolumesByStackIdAndSearch(stackId, null)
                .stream().map(SerServiceImpl::toVolume).toList();
        for (ServiceVo serviceVo : serviceVos) {
            serviceVo.setPorts(portMap.getOrDefault(serviceVo.getId(), Collections.emptyList()));
            serviceVo.setVolumes(volumes);
        }
    }

    private static Port toPort(PortDo portDo) {
        Port port = new Port();
        port.setId(portDo.getId());
        port.setStackId(portDo.getStackId());
        port.setProtocol(portDo.getProtocol());
        port.setPort(portDo.getPort());
        port.setServiceId(portDo.getServiceId());
        return port;
    }

    private static Volume toVolume(VolumeDo volumeDo) {
        Volume volume = new Volume();
        volume.setId(volumeDo.getId());
        volume.setStackId(volumeDo.getStackId());
        volume.setVolumeName(volumeDo.getVolumeName());
        volume.setSize(volumeDo.getSize());
        return volume;
    }

    private void recordHistory(Integer stackId, String event) {
        CreateHistoryDto createHistoryDto = new CreateHistoryDto();
        createHistoryDto.setStackId(stackId);
        createHistoryDto.setEvent(event);
        historyService.createHistory(createHistoryDto);
    }
}
