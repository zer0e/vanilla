package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateHistoryDto;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.infrastructure.db.repository.OperationHistoryDo;

public interface HistoryService {

    OperationHistoryDo createHistory(CreateHistoryDto createHistoryDto);

    PageData<OperationHistoryDo> getHistoryByStackIdOrderByCreateTimeDesc(Integer stackId);
}
