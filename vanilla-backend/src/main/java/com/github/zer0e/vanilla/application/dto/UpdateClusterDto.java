package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateClusterDto {
    @NotNull(message = "集群id不能为空")
    private Integer id;
    private String clusterName;
    private String description;

    /**
     * 集群类型 DOCKER / K8S
     */
    private String type;

    /**
     * Docker daemon 连接地址
     */
    private String endpoint;

    /**
     * 是否启用 TLS 校验
     */
    private Boolean tlsVerify;

    /**
     * Docker TLS 证书目录（兼容直接指定服务器目录的场景）
     */
    private String dockerCertPath;

    /**
     * CA 证书（PEM，上传后存库）
     */
    private String caCert;

    /**
     * 客户端证书（PEM）
     */
    private String clientCert;

    /**
     * 客户端私钥（PEM）
     */
    private String clientKey;

    /**
     * 普通成员用户id列表
     */
    private List<Integer> userIds;
}
