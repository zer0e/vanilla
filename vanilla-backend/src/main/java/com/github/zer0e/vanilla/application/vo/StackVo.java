package com.github.zer0e.vanilla.application.vo;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class StackVo {
    private Integer id;

    private String stackName;

    private String description;

    private String owner;

    private String createUser;

    private LocalDateTime createTime;

    private String modifyUser;

    private LocalDateTime modifyTime;
}
