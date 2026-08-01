package com.github.zer0e.vanilla.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.infrastructure.db.mapper.OperationHistoryMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.OperationHistoryDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final OperationHistoryMapper operationHistoryMapper;

    @Override
    public OperationHistoryDo createHistory(CreateHistoryDto createHistoryDto) {
        OperationHistoryDo historyDo = OperationHistoryDo.builder()
                .stackId(createHistoryDto.getStackId())
                .event(createHistoryDto.getEvent())
                .createUser(SecurityUtil.getCurrentUserName())
                .createTime(LocalDateTime.now())
                .build();
        operationHistoryMapper.insert(historyDo);
        return historyDo;
    }

    @Override
    @PreAuthorize("hasAnyRole('stack_' + #stackId + '_stack_admin'," +
            "'stack_' + #stackId + '_stack_member'," +
            "'stack_' + #stackId + '_stack_readonly')")
    public PageData<OperationHistoryDo> getHistoryByStackId(Integer stackId, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        LambdaQueryWrapper<OperationHistoryDo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OperationHistoryDo::getStackId, stackId)
                .orderByDesc(OperationHistoryDo::getCreateTime);
        List<OperationHistoryDo> historyDos = operationHistoryMapper.selectList(wrapper);
        PageInfo<OperationHistoryDo> pageInfo = new PageInfo<>(historyDos);
        return new PageData<>(page, size, pageInfo.getTotal(), historyDos);
    }
}
