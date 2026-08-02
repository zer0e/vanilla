package com.github.zer0e.vanilla.domain;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class Cluster {
    private Integer id;
    private String clusterName;
    private String description;

    /**
     * 集群类型 DOCKER / K8S
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
     * Docker TLS 证书目录（兼容直接指定服务器目录的场景）
     */
    private String dockerCertPath;

    /**
     * CA 证书（PEM，SQL 型存储），用户上传后写入数据库
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
}
