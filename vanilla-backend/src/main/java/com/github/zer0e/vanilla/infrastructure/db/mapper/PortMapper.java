package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PortDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PortMapper extends BaseMapper<PortDo> {
    PortDo selectByServiceIdAndPort(@Param("serviceId") Integer serviceId, @Param("port") Integer port);

    List<PortDo> selectPortsByServiceId(@Param("serviceId") Integer serviceId);
}
