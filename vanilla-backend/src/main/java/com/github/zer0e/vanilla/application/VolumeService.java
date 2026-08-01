package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateVolumeDto;
import com.github.zer0e.vanilla.application.dto.DeleteVolumeDto;
import com.github.zer0e.vanilla.application.dto.GetVolumesDto;
import com.github.zer0e.vanilla.application.dto.UpdateVolumeDto;
import com.github.zer0e.vanilla.application.vo.VolumeVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface VolumeService {

    VolumeVo createVolume(CreateVolumeDto createVolumeDto) throws BusinessException;

    VolumeVo updateVolume(UpdateVolumeDto updateVolumeDto) throws BusinessException;

    void deleteVolume(DeleteVolumeDto deleteVolumeDto) throws BusinessException;

    PageData<VolumeVo> getVolumes(GetVolumesDto getVolumesDto) throws BusinessException;
}
