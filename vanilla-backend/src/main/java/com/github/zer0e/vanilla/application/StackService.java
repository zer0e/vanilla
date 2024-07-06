package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateStackDto;
import com.github.zer0e.vanilla.application.dto.GetStacksDto;
import com.github.zer0e.vanilla.application.dto.UpdateStackDto;
import com.github.zer0e.vanilla.application.vo.StackVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;

/**
 * The interface Stack service.
 */
public interface StackService {

    /**
     * Create stack stack vo.
     *
     * @param createStackDto the creation stack dto
     * @return the stack vo
     * @throws BusinessException the business exception
     */
    StackVo createStack(CreateStackDto createStackDto) throws BusinessException;

    /**
     * Update stack stack vo.
     *
     * @param updateStackDto the update stack dto
     * @return the stack vo
     * @throws BusinessException the business exception
     */
    StackVo updateStack(UpdateStackDto updateStackDto) throws BusinessException;

    /**
     * Delete stack.
     *
     * @param updateStackDto the update stack dto
     * @throws BusinessException the business exception
     */
    void deleteStack(UpdateStackDto updateStackDto) throws BusinessException;


    /**
     * 获取当前用户有权限的栈
     *
     * @param getStacksDto the get stacks dto
     * @return the stacks
     * @throws BusinessException the business exception
     */
    PageData<StackVo> getStacks(GetStacksDto getStacksDto) throws BusinessException;

}
