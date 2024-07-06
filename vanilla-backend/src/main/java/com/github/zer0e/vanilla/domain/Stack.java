package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Stack {
    private Integer id;
    private String StackName;
    private String description;
    private String owner;

    private String createUser;
    private LocalDateTime createTime;

}
