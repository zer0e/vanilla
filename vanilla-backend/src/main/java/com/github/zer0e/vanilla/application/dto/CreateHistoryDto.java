package com.github.zer0e.vanilla.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class CreateHistoryDto {
    private Integer stackId;
    private String event;

    private LocalDateTime createTime;
    private String createUser;
}
