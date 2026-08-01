package com.github.zer0e.vanilla.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GetUsersDto {

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1")
    private Integer page;

    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "15")
    private Integer size;

    /**
     * 按登录名/昵称模糊搜索
     */
    private String search;
}
