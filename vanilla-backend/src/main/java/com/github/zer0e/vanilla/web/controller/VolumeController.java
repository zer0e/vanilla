package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.VolumeService;
import com.github.zer0e.vanilla.application.dto.CreateVolumeDto;
import com.github.zer0e.vanilla.application.dto.DeleteVolumeDto;
import com.github.zer0e.vanilla.application.dto.GetVolumesDto;
import com.github.zer0e.vanilla.application.dto.UpdateVolumeDto;
import com.github.zer0e.vanilla.application.vo.VolumeVo;
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
@Tag(name = "卷相关")
@RequestMapping("/volume/api")
@RequiredArgsConstructor
public class VolumeController {

    private final VolumeService volumeService;

    @PostMapping("/v1/create")
    @Operation(summary = "创建卷")
    public RestResponse<VolumeVo> createVolume(@RequestBody @Valid CreateVolumeDto createVolumeDto) throws BusinessException {
        return RestResponse.ok(volumeService.createVolume(createVolumeDto));
    }

    @PostMapping("/v1/update")
    @Operation(summary = "修改卷")
    public RestResponse<VolumeVo> updateVolume(@RequestBody @Valid UpdateVolumeDto updateVolumeDto) throws BusinessException {
        return RestResponse.ok(volumeService.updateVolume(updateVolumeDto));
    }

    @PostMapping("/v1/delete")
    @Operation(summary = "删除卷")
    public RestResponse<Void> deleteVolume(@RequestBody @Valid DeleteVolumeDto deleteVolumeDto) throws BusinessException {
        volumeService.deleteVolume(deleteVolumeDto);
        return RestResponse.ok(null);
    }

    @PostMapping("/v1/list")
    @Operation(summary = "获取栈下的卷")
    public RestResponse<PageData<VolumeVo>> getVolumes(@RequestBody @Valid GetVolumesDto getVolumesDto) throws BusinessException {
        return RestResponse.ok(volumeService.getVolumes(getVolumesDto));
    }
}
