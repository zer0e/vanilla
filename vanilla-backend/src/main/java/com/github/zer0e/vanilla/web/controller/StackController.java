package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.DeployService;
import com.github.zer0e.vanilla.application.StackService;
import com.github.zer0e.vanilla.application.dto.ContainerLogsDto;
import com.github.zer0e.vanilla.application.dto.CreateStackDto;
import com.github.zer0e.vanilla.application.dto.DeployStackDto;
import com.github.zer0e.vanilla.application.dto.GetStacksDto;
import com.github.zer0e.vanilla.application.dto.UpdateStackDto;
import com.github.zer0e.vanilla.application.vo.ContainerLogVo;
import com.github.zer0e.vanilla.application.vo.StackStatusVo;
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
    private final DeployService deployService;

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

    @PostMapping("/v1/deploy")
    @Operation(summary = "部署栈到目标集群")
    public RestResponse<StackStatusVo> deployStack(@RequestBody @Valid DeployStackDto deployStackDto) throws BusinessException {
        return RestResponse.ok(deployService.deployStack(deployStackDto));
    }

    @PostMapping("/v1/status")
    @Operation(summary = "查询栈运行状态")
    public RestResponse<StackStatusVo> getStackStatus(@RequestBody @Valid DeployStackDto deployStackDto) throws BusinessException {
        return RestResponse.ok(deployService.getStackStatus(deployStackDto));
    }

    @PostMapping("/v1/stop")
    @Operation(summary = "停止栈")
    public RestResponse<Void> stopStack(@RequestBody @Valid DeployStackDto deployStackDto) throws BusinessException {
        deployService.stopStack(deployStackDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/remove")
    @Operation(summary = "下架栈")
    public RestResponse<Void> removeStack(@RequestBody @Valid DeployStackDto deployStackDto) throws BusinessException {
        deployService.removeStack(deployStackDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/logs")
    @Operation(summary = "查看服务容器日志")
    public RestResponse<ContainerLogVo> getContainerLog(@RequestBody @Valid ContainerLogsDto containerLogsDto) throws BusinessException {
        return RestResponse.ok(deployService.getContainerLog(containerLogsDto));
    }
}
