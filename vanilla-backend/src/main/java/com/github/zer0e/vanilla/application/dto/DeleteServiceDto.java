package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteServiceDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @NotNull(message = "服务id不能为空")
    private Integer serviceId;
}
