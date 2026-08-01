package com.github.zer0e.vanilla.infrastructure.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.OperationHistoryDo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationHistoryMapper extends BaseMapper<OperationHistoryDo> {
}
