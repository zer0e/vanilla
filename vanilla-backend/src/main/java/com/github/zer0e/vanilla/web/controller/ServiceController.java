package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.SerService;
import com.github.zer0e.vanilla.application.dto.CreateServiceDto;
import com.github.zer0e.vanilla.application.dto.DeleteServiceDto;
import com.github.zer0e.vanilla.application.dto.GetServicesDto;
import com.github.zer0e.vanilla.application.dto.UpdateServiceDto;
import com.github.zer0e.vanilla.application.vo.ServiceVo;
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
@Tag(name = "服务相关")
@RequestMapping("/service/api")
@RequiredArgsConstructor
public class ServiceController {

    private final SerService serService;

    @PostMapping("/v1/create")
    @Operation(summary = "创建服务")
    public RestResponse<ServiceVo> createService(@RequestBody @Valid CreateServiceDto createServiceDto) throws BusinessException {
        return RestResponse.ok(serService.createService(createServiceDto));
    }

    @PostMapping("/v1/update")
    @Operation(summary = "修改服务")
    public RestResponse<ServiceVo> updateService(@RequestBody @Valid UpdateServiceDto updateServiceDto) throws BusinessException {
        return RestResponse.ok(serService.updateService(updateServiceDto));
    }

    @PostMapping("/v1/delete")
    @Operation(summary = "删除服务")
    public RestResponse<Void> deleteService(@RequestBody @Valid DeleteServiceDto deleteServiceDto) throws BusinessException {
        serService.deleteService(deleteServiceDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/list")
    @Operation(summary = "获取栈下的服务")
    public RestResponse<PageData<ServiceVo>> getServices(@RequestBody @Valid GetServicesDto getServicesDto) throws BusinessException {
        return RestResponse.ok(serService.getServices(getServicesDto));
    }
}
