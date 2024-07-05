package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.domain.User;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserConverter {
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    User toUser(UserDo userDo);
}
