package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.StackDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StackMapper extends BaseMapper<StackDo> {
    StackDo selectByClusterIdAndName(@Param("clusterId") Integer clusterId, @Param("stackName") String stackName);

    List<StackDo> selectStacksByClusterIdAndStackIds(@Param("clusterId") Integer clusterId,
                                                     @Param("stackIds") List<Integer> stackIds,
                                                     @Param("search") String search);
}
