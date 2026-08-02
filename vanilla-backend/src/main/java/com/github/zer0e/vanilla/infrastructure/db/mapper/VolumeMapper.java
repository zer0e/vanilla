package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.VolumeDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VolumeMapper extends BaseMapper<VolumeDo> {

    VolumeDo selectByStackIdAndName(@Param("stackId") Integer stackId, @Param("volumeName") String volumeName);

    List<VolumeDo> selectVolumesByStackIdAndSearch(@Param("stackId") Integer stackId, @Param("search") String search);

    /**
     * 查询服务引用的卷（经 t_service_volume 关联）
     */
    List<VolumeDo> selectVolumesByServiceIds(@Param("serviceIds") List<Integer> serviceIds);
}