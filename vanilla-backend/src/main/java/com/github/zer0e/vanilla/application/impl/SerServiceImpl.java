package com.github.zer0e.vanilla.application.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.SerService;
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
import com.github.zer0e.vanilla.infrastructure.converter.ServiceConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.StackMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class SerServiceImpl implements SerService {

    private final ServiceMapper serviceMapper;
    private final StackMapper stackMapper;

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
        PageInfo<ServiceDo> pageInfo = new PageInfo<>(serviceDos);
        return new PageData<>(page, size, pageInfo.getTotal(), serviceVos);
    }
}
