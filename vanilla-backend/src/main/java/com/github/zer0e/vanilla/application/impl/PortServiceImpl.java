package com.github.zer0e.vanilla.application.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.PortService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.application.dto.CreatePortDto;
import com.github.zer0e.vanilla.application.dto.DeletePortDto;
import com.github.zer0e.vanilla.application.dto.GetPortsDto;
import com.github.zer0e.vanilla.application.dto.UpdatePortDto;
import com.github.zer0e.vanilla.application.vo.PortVo;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.infrastructure.converter.PortConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.PortMapper;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ServiceMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortServiceImpl implements PortService {

    private final PortMapper portMapper;
    private final ServiceMapper serviceMapper;
    private final HistoryService historyService;

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #createPortDto.stackId + '_stack_admin'," +
            "'stack_' + #createPortDto.stackId + '_stack_member')")
    @Transactional(rollbackFor = Exception.class)
    public PortVo createPort(CreatePortDto createPortDto) throws BusinessException {
        ServiceDo serviceDo = checkService(createPortDto.getStackId(), createPortDto.getServiceId());
        PortDo repeat = portMapper.selectByServiceIdAndPort(createPortDto.getServiceId(), createPortDto.getPort());
        if (repeat != null) {
            throw new BusinessException(Constants.PORT_DUPLICATE);
        }
        PortDo portDo = PortConverter.INSTANCE.toDo(createPortDto);
        String currentUserName = SecurityUtil.getCurrentUserName();
        portDo.setStatus(DataStatus.EXIST.ordinal());
        portDo.setCreateUser(currentUserName);
        portDo.setCreateTime(LocalDateTime.now());
        portMapper.insert(portDo);
        recordHistory(createPortDto.getStackId(), "为服务 " + serviceDo.getServiceName() + " 添加端口 " + portDo.getProtocol() + ":" + portDo.getPort());
        return PortConverter.INSTANCE.toVo(portDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #updatePortDto.stackId + '_stack_admin')")
    @Transactional(rollbackFor = Exception.class)
    public PortVo updatePort(UpdatePortDto updatePortDto) throws BusinessException {
        ServiceDo serviceDo = checkService(updatePortDto.getStackId(), updatePortDto.getServiceId());
        PortDo portDo = portMapper.selectById(updatePortDto.getId());
        if (portDo == null || portDo.getStatus() != DataStatus.EXIST.ordinal()
                || !Objects.equals(portDo.getServiceId(), updatePortDto.getServiceId())) {
            throw new BusinessException(Constants.PORT_NOT_EXIST);
        }
        PortDo repeat = portMapper.selectByServiceIdAndPort(updatePortDto.getServiceId(), updatePortDto.getPort());
        if (repeat != null && !Objects.equals(repeat.getId(), updatePortDto.getId())) {
            throw new BusinessException(Constants.PORT_DUPLICATE);
        }
        BeanUtils.copyProperties(updatePortDto, portDo);
        portDo.setModifyTime(LocalDateTime.now());
        portDo.setModifyUser(SecurityUtil.getCurrentUserName());
        portMapper.updateById(portDo);
        recordHistory(updatePortDto.getStackId(), "更新服务 " + serviceDo.getServiceName() + " 的端口为 " + portDo.getProtocol() + ":" + portDo.getPort());
        return PortConverter.INSTANCE.toVo(portDo);
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #deletePortDto.stackId + '_stack_admin')")
    public void deletePort(DeletePortDto deletePortDto) throws BusinessException {
        ServiceDo serviceDo = checkService(deletePortDto.getStackId(), deletePortDto.getServiceId());
        PortDo portDo = portMapper.selectById(deletePortDto.getId());
        if (portDo == null || portDo.getStatus() != DataStatus.EXIST.ordinal()
                || !Objects.equals(portDo.getServiceId(), deletePortDto.getServiceId())) {
            throw new BusinessException(Constants.PORT_NOT_EXIST);
        }
        portDo.setStatus(DataStatus.NOT_EXIST.ordinal());
        portDo.setDeleteTime(LocalDateTime.now());
        portDo.setDeleteUser(SecurityUtil.getCurrentUserName());
        portMapper.updateById(portDo);
        recordHistory(deletePortDto.getStackId(), "删除服务 " + serviceDo.getServiceName() + " 的端口 " + portDo.getProtocol() + ":" + portDo.getPort());
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #getPortsDto.stackId + '_stack_admin'," +
            "'stack_' + #getPortsDto.stackId + '_stack_member'," +
            "'stack_' + #getPortsDto.stackId + '_stack_readonly')")
    public PageData<PortVo> getPorts(GetPortsDto getPortsDto) throws BusinessException {
        Integer serviceId = getPortsDto.getServiceId();
        Integer size = getPortsDto.getSize();
        Integer page = getPortsDto.getPage();
        PageHelper.startPage(page, size);
        List<PortDo> portDos = portMapper.selectPortsByServiceId(serviceId);
        List<PortVo> portVos = portDos.stream().map(PortConverter.INSTANCE::toVo).toList();
        PageInfo<PortDo> pageInfo = new PageInfo<>(portDos);
        return new PageData<>(page, size, pageInfo.getTotal(), portVos);
    }

    private ServiceDo checkService(Integer stackId, Integer serviceId) throws BusinessException {
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
