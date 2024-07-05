package com.github.zer0e.vanilla.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolePermission implements GrantedAuthority {
    private Integer stackId;
    private String permission;

    public UserRolePermission(String permission) {
        this.permission = permission;
    }

    @Override
    public String getAuthority() {
        if (stackId != null) {
            return stackId + "_" + permission;
        }
        return permission;
    }
}
