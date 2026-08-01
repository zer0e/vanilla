package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ServiceDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ServiceMapper extends BaseMapper<ServiceDo> {
    ServiceDo selectByStackIdAndName(@Param("stackId") Integer stackId, @Param("serviceName") String serviceName);

    List<ServiceDo> selectServicesByStackIdAndSearch(@Param("stackId") Integer stackId, @Param("search") String search);
}
