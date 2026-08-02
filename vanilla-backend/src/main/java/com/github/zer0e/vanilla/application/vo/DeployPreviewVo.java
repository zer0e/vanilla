package com.github.zer0e.vanilla.application.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部署预览：K8s 集群返回即将创建的资源 YAML；Docker 集群不支持（supported=false）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeployPreviewVo {

    /**
     * 是否支持预览（仅 K8s 集群）
     */
    private Boolean supported;

    /**
     * 生成的 K8s 资源 YAML（多文档，--- 分隔）
     */
    private String yaml;

    public static DeployPreviewVo of(String yaml) {
        return DeployPreviewVo.builder().supported(true).yaml(yaml).build();
    }

    public static DeployPreviewVo unsupported() {
        return DeployPreviewVo.builder().supported(false).build();
    }
}