package com.github.zer0e.vanilla.infrastructure.converter;

import com.github.zer0e.vanilla.application.dto.CreateClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.domain.Cluster;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ClusterConverter {

    ClusterConverter INSTANCE = Mappers.getMapper(ClusterConverter.class);

    Cluster toPojo(CreateClusterDto createClusterDto);
    ClusterVo toVo(Cluster cluster);
    ClusterVo toVo(ClusterDo cluster);
    ClusterDo toDo(Cluster cluster);
    ClusterDo toDo(CreateClusterDto createClusterDto);
}
