package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserDo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<UserDo> {

    @Select("select * from t_user where login_name = #{loginName} and status = 0")
    UserDo findByLoginName(String loginName);

    List<UserDo> selectUsersBySearch(@Param("search") String search);
}
