package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.PermissionDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.RolePermissionDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<RoleDo> {

    List<UserRoleDo> selectRoleIdsByUserId(@Param("userId") Integer userId);

    List<String> selectRoleNameByIds(@Param("ids") List<Integer> ids);

    List<RoleDo> selectRoleByIds(@Param("ids") List<Integer> ids);

    List<String> selectPermissionByRoleIds(@Param("roleIds") List<Integer> roleIds);

    List<RolePermissionDo> selectRolePermissionByRoleIds(@Param("roleIds") List<Integer> roleIds);

    List<PermissionDo> selectPermissionByIds(@Param("ids") List<Integer> ids);
}
