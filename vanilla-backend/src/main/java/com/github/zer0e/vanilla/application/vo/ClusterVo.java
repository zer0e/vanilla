package com.github.zer0e.vanilla.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
public class ClusterVo {
    private Integer id;
    private String name;
    @Schema(description = "描述")
    private String description;
}
