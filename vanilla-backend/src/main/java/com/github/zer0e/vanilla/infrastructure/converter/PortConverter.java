package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.application.dto.CreatePortDto;
import com.github.zer0e.vanilla.application.vo.PortVo;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PortConverter {
    PortConverter INSTANCE = Mappers.getMapper(PortConverter.class);

    PortDo toDo(CreatePortDto createPortDto);

    PortVo toVo(PortDo portDo);
}
