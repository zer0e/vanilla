package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateVolumeDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @NotEmpty(message = "卷名称不能为空")
    private String volumeName;

    /**
     * 卷大小 单位GB
     */
    private Integer size;
}
