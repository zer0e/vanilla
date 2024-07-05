package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class UpdateClusterDto extends CreateClusterDto {
    @NotNull(message = "集群id不能为空")
    private Integer id;
}
