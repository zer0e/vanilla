package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStackDto {
    @NotNull(message = "集群id不能为null")
    private Integer clusterId;
    @NotEmpty(message = "栈名称不能为空")
    private String stackName;
    private String description;
}
