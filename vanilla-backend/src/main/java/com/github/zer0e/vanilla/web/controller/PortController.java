package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.PortService;
import com.github.zer0e.vanilla.application.dto.CreatePortDto;
import com.github.zer0e.vanilla.application.dto.DeletePortDto;
import com.github.zer0e.vanilla.application.dto.GetPortsDto;
import com.github.zer0e.vanilla.application.dto.UpdatePortDto;
import com.github.zer0e.vanilla.application.vo.PortVo;
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
@Tag(name = "端口相关")
@RequestMapping("/port/api")
@RequiredArgsConstructor
public class PortController {

    private final PortService portService;

    @PostMapping("/v1/create")
    @Operation(summary = "创建端口")
    public RestResponse<PortVo> createPort(@RequestBody @Valid CreatePortDto createPortDto) throws BusinessException {
        return RestResponse.ok(portService.createPort(createPortDto));
    }

    @PostMapping("/v1/update")
    @Operation(summary = "修改端口")
    public RestResponse<PortVo> updatePort(@RequestBody @Valid UpdatePortDto updatePortDto) throws BusinessException {
        return RestResponse.ok(portService.updatePort(updatePortDto));
    }

    @PostMapping("/v1/delete")
    @Operation(summary = "删除端口")
    public RestResponse<Void> deletePort(@RequestBody @Valid DeletePortDto deletePortDto) throws BusinessException {
        portService.deletePort(deletePortDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/list")
    @Operation(summary = "获取服务下的端口")
    public RestResponse<PageData<PortVo>> getPorts(@RequestBody @Valid GetPortsDto getPortsDto) throws BusinessException {
        return RestResponse.ok(portService.getPorts(getPortsDto));
    }
}
