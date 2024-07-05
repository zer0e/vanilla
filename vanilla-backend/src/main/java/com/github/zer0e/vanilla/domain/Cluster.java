package com.github.zer0e.vanilla.domain;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class Cluster {
    private Integer id;
    private String clusterName;
    private String description;
}
