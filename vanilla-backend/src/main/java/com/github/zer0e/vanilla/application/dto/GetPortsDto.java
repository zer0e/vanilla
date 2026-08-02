package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetPortsDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    /**
     * 服务 id：为空时返回整个栈的端口（端口/SVC 管理页）
     */
    private Integer serviceId;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1")
    private Integer page;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "15")
    private Integer size;

    private String search;
}
