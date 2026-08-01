package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface DeployService {

    StackStatusVo deployStack(DeployStackDto deployStackDto) throws BusinessException;

    StackStatusVo getStackStatus(DeployStackDto deployStackDto) throws BusinessException;

    void stopStack(DeployStackDto deployStackDto) throws BusinessException;

    void removeStack(DeployStackDto deployStackDto) throws BusinessException;
}
