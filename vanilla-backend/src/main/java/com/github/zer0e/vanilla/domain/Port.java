package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Port {
    private Integer id;
    private Integer stackId;
    private String protocol;
    private Integer port;
    private Integer serviceId;

}
