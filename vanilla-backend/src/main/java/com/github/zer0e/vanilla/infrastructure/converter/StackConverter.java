package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.application.dto.CreateStackDto;
import com.github.zer0e.vanilla.application.vo.StackVo;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface StackConverter {
    StackConverter INSTANCE = Mappers.getMapper(StackConverter.class);

    StackDo toDo(CreateStackDto createStackDto);

    StackVo toVo(StackDo stackDo);
}
