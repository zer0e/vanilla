package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

@Data
public class PortVo {
    private Integer id;

    private Integer serviceId;

    private Integer stackId;

    private String protocol;

    private Integer port;
}
