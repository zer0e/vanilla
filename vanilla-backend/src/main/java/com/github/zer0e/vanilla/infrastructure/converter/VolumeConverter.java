package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.application.dto.CreateVolumeDto;
import com.github.zer0e.vanilla.application.vo.VolumeVo;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface VolumeConverter {
    VolumeConverter INSTANCE = Mappers.getMapper(VolumeConverter.class);

    VolumeDo toDo(CreateVolumeDto createVolumeDto);

    VolumeVo toVo(VolumeDo volumeDo);
}
