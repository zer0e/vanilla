package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleDo> {

    List<String> selectRoleNameByIds(@Param("ids") List<Integer> ids);

    List<RoleDo> selectRoleByIds(@Param("ids") List<Integer> ids);

    List<String> selectPermissionByRoleIds(@Param("roleIds") List<Integer> roleIds);

}
