package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务声明需暴露的端口（在服务创建/编辑表单中声明；
 * SVC 类型不在此处，由「端口/SVC」管理页按端口配置）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortSpecDto {

    @Schema(description = "协议 tcp/udp，默认 tcp")
    private String protocol;

    @Schema(description = "端口号")
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口范围 1-65535")
    @Max(value = 65535, message = "端口范围 1-65535")
    private Integer port;
}