package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteVolumeDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @NotNull(message = "卷id不能为空")
    private Integer id;
}
