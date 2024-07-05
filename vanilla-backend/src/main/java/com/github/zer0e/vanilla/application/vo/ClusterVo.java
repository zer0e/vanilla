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

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    private String createUser;

    @Schema(description = "修改时间")
    private LocalDateTime modifyTime;
    private String modifyUser;
}
