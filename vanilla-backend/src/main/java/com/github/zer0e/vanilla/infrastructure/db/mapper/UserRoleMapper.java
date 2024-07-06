package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
@Mapper
public interface UserRoleMapper  extends BaseMapper<UserRoleDo> {

    List<UserRoleDo> selectUserRolesByUserId(@Param("userId") Integer userId);

    List<UserRoleDo> selectUserRolesByClusterId(@Param("clusterId") Integer clusterId);

    List<Integer> selectHasPermissionStackIdsByUserId(@Param("userId") Integer userId);
}
