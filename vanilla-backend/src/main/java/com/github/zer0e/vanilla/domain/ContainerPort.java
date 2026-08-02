package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 容器/Pod 暴露端口（服务表单声明，对应 K8s containerPort / Docker EXPOSE）。
 * 与「端口访问」页的 SVC（t_port）相互独立：SVC 创建时可引用这里的端口
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerPort {
    /**
     * 协议 tcp/udp，默认 tcp
     */
    private String protocol;

    /**
     * 容器监听端口
     */
    private Integer port;
}