package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ClusterDto {
    @NotNull(message = "集群名称不能为null")
    private String clusterName;
    private String description;
}
