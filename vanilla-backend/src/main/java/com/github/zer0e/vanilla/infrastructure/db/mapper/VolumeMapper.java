package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VolumeMapper extends BaseMapper<VolumeDo> {
    VolumeDo selectByServiceIdAndName(@Param("serviceId") Integer serviceId, @Param("volumeName") String volumeName);

    List<VolumeDo> selectVolumesByServiceIdAndSearch(@Param("serviceId") Integer serviceId, @Param("search") String search);

    List<VolumeDo> selectVolumesByServiceIds(@Param("serviceIds") List<Integer> serviceIds);
}
