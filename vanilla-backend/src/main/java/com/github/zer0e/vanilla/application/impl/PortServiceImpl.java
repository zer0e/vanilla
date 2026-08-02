package com.github.zer0e.vanilla.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortServiceImpl implements PortService {

    private static final Set<String> SUPPORTED_SERVICE_TYPES = Set.of("ClusterIP", "NodePort", "LoadBalancer");

    private final PortMapper portMapper;
    private final ServiceMapper serviceMapper;
    private final HistoryService historyService;

    /**
     * 校验 SVC 类型：ClusterIP / NodePort / LoadBalancer；留空 = 自动
     */
    private void validateServiceType(String serviceType) throws BusinessException {
        if (StringUtils.hasText(serviceType) && !SUPPORTED_SERVICE_TYPES.contains(serviceType)) {
            throw new BusinessException("不支持的服务类型：" + serviceType
                    + "（可选 ClusterIP / NodePort / LoadBalancer，留空为自动）");
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #createPortDto.stackId + '_stack_admin'," +
            "'stack_' + #createPortDto.stackId + '_stack_member')")
    @Transactional(rollbackFor = Exception.class)
    public PortVo createPort(CreatePortDto createPortDto) throws BusinessException {
        validateServiceType(createPortDto.getServiceType());
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
        validateServiceType(updatePortDto.getServiceType());
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
        // 物理删除端口：释放 uk_service_port 唯一键，允许同名端口重新创建
        portMapper.deleteById(portDo.getId());
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
        String search = getPortsDto.getSearch();
        PageHelper.startPage(page, size);
        List<PortDo> portDos;
        if (serviceId != null) {
            portDos = portMapper.selectPortsByServiceId(serviceId);
        } else {
            // 端口/SVC 管理页：按栈查询全部端口并回填服务名
            portDos = portMapper.selectPortsByStackIdAndSearch(getPortsDto.getStackId(), search);
        }
        List<PortVo> portVos = portDos.stream().map(PortConverter.INSTANCE::toVo).toList();
        fillServiceName(getPortsDto.getStackId(), portVos);
        PageInfo<PortDo> pageInfo = new PageInfo<>(portDos);
        return new PageData<>(page, size, pageInfo.getTotal(), portVos);
    }

    /**
     * 按服务 map 回填端口所属服务名（用于栈级端口管理页）
     */
    private void fillServiceName(Integer stackId, List<PortVo> portVos) {
        if (portVos.isEmpty()) {
            return;
        }
        Map<Integer, String> nameById = serviceMapper.selectServicesByStackIdAndSearch(stackId, null).stream()
                .collect(Collectors.toMap(ServiceDo::getId, ServiceDo::getServiceName));
        portVos.forEach(p -> p.setServiceName(nameById.get(p.getServiceId())));
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
