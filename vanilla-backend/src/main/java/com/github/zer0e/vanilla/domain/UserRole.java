package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    private Integer userId;
    private Integer roleId;
    private Integer stackId;
    private Integer clusterId;
}
