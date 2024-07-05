package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.RolePermissionDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermissionDo> {
    List<RolePermissionDo> selectRolePermissionByRoleIds(@Param("roleIds") List<Integer> roleIds);
}
