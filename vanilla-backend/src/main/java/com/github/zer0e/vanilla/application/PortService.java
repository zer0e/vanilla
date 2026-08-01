package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreatePortDto;
import com.github.zer0e.vanilla.application.dto.DeletePortDto;
import com.github.zer0e.vanilla.application.dto.GetPortsDto;
import com.github.zer0e.vanilla.application.dto.UpdatePortDto;
import com.github.zer0e.vanilla.application.vo.PortVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface PortService {

    PortVo createPort(CreatePortDto createPortDto) throws BusinessException;

    PortVo updatePort(UpdatePortDto updatePortDto) throws BusinessException;

    void deletePort(DeletePortDto deletePortDto) throws BusinessException;

    PageData<PortVo> getPorts(GetPortsDto getPortsDto) throws BusinessException;
}
