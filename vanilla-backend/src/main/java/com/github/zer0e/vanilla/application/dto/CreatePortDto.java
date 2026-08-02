package com.github.zer0e.vanilla.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePortDto {

    @NotNull(message = "栈id不能为空")
    private Integer stackId;

    @NotNull(message = "服务id不能为空")
    private Integer serviceId;

    private String protocol;

    @NotNull(message = "端口不能为空")
    private Integer port;

    /**
     * K8s Service 类型：ClusterIP / NodePort / LoadBalancer；空 = 自动
     */
    private String serviceType;
}
