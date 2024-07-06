package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetStacksDto {

    @NotNull(message = "集群id不能为空")
    private Integer clusterId;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1")
    private Integer page;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "15")
    private Integer size;

    private String search;
}
