package com.github.zer0e.vanilla.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
public class ClusterVo {
    private Integer id;
    private String clusterName;
    @Schema(description = "描述")
    private String description;

    @Schema(description = "集群类型 DOCKER / K8S")
    private String type;

    @Schema(description = "Docker daemon 连接地址")
    private String endpoint;

    @Schema(description = "是否启用 TLS 校验")
    private Boolean tlsVerify;

    @Schema(description = "Docker TLS 证书目录")
    private String dockerCertPath;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    private String createUser;

    @Schema(description = "修改时间")
    private LocalDateTime modifyTime;
    private String modifyUser;
}
