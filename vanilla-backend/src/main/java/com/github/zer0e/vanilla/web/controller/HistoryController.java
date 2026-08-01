package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.HistoryService;
import com.github.zer0e.vanilla.application.dto.GetHistoryDto;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.infrastructure.db.repository.OperationHistoryDo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "操作记录相关")
@RequestMapping("/history/api")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @PostMapping("/v1/list")
    @Operation(summary = "获取栈下的操作记录")
    public RestResponse<PageData<OperationHistoryDo>> getHistory(@RequestBody @Valid GetHistoryDto getHistoryDto) {
        return RestResponse.ok(historyService.getHistoryByStackId(
                getHistoryDto.getStackId(), getHistoryDto.getPage(), getHistoryDto.getSize()));
    }
}
