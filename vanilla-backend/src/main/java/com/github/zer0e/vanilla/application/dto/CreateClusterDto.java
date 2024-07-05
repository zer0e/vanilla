package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CreateClusterDto {
    @NotNull(message = "集群名称不能为null")
    private String clusterName;
    private String description;
    private List<Integer> userIds;
}
