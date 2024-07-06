package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.StackService;
import com.github.zer0e.vanilla.application.dto.CreateStackDto;
import com.github.zer0e.vanilla.application.dto.GetStacksDto;
import com.github.zer0e.vanilla.application.dto.UpdateStackDto;
import com.github.zer0e.vanilla.application.vo.StackVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "栈相关")
@RequestMapping("/stack/api")
@RequiredArgsConstructor
public class StackController {

    private final StackService stackService;
    @PostMapping("/v1/create")
    @Operation(summary = "创建栈")
    public RestResponse<StackVo> createStack(@RequestBody @Valid CreateStackDto createStackDto) throws BusinessException {
        return RestResponse.ok(stackService.createStack(createStackDto));
    }

    @PostMapping("/v1/update")
    @Operation(summary = "修改栈")
    public RestResponse<StackVo> updateStack(@RequestBody @Valid UpdateStackDto updateStackDto) throws BusinessException {
        return RestResponse.ok(stackService.updateStack(updateStackDto));
    }

    @PostMapping("/v1/delete")
    @Operation(summary = "删除栈")
    public RestResponse<StackVo> deleteStack(@RequestBody @Valid UpdateStackDto updateStackDto) throws BusinessException {
        stackService.deleteStack(updateStackDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/list")
    @Operation(summary = "获取集群下有权限的栈")
    public RestResponse<PageData<StackVo>> deleteStack(@RequestBody @Valid GetStacksDto getStacksDto) throws BusinessException {
        return RestResponse.ok(stackService.getStacks(getStacksDto));
    }
}
