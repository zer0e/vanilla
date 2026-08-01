package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStackDto {
    @NotNull(message = "栈id不能为空")
    private Integer id;

    private String stackName;

    private String description;

}
