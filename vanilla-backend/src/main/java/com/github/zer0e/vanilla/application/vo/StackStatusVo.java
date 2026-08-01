package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

import java.util.List;

/**
 * 栈运行状态
 */
@Data
public class StackStatusVo {
    private Integer stackId;

    /**
     * RUNNING / STOPPED / PARTIAL / NONE
     */
    private String status;

    private List<ServiceStatusVo> services;
}
