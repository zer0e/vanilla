package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerLogsDto {

    @Schema(description = "栈id")
    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @Schema(description = "服务id")
    @NotNull(message = "服务id不能为空")
    private Integer serviceId;

    @Schema(description = "副本索引（多副本时指定，缺省取第一个运行的副本）")
    private Integer replicaIndex;

    @Schema(description = "日志行数，默认 500，范围 1~10000")
    private Integer tail;
}