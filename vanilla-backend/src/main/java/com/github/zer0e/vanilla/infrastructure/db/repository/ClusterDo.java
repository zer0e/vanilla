package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Data
@ToString
@TableName(value = "t_cluster")
public class ClusterDo extends Base {
    @TableId(type = IdType.AUTO)
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
     * CA 证书（PEM），用户上传后存库
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
