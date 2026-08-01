package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.application.vo.ServiceVo;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ServiceConverter {
    ServiceConverter INSTANCE = Mappers.getMapper(ServiceConverter.class);

    ServiceVo toVo(ServiceDo serviceDo);
}
