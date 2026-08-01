package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateVolumeDto {

    @NotNull(message = "卷id不能为空")
    private Integer id;

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    /**
     * 卷大小 单位GB
     */
    private Integer size;

    /**
     * 容器内挂载路径
     */
    private String mountPath;
}
