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

    /**
     * 集群类型 DOCKER / K8S，默认 DOCKER
     */
    private String type;

    /**
     * Docker daemon 连接地址，如 tcp://192.168.1.100:2375
     */
    private String endpoint;

    /**
     * 是否启用 TLS 校验
     */
    private Boolean tlsVerify;

    /**
     * Docker TLS 证书目录
     */
    private String dockerCertPath;
}
