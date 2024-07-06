package com.github.zer0e.vanilla.domain;

import com.github.zer0e.vanilla.common.StringConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRolePermission implements GrantedAuthority {
    private Integer clusterId;
    private Integer stackId;
    private Boolean role;
    private String permission;

    private String authority;

    public UserRolePermission(Integer clusterId, Integer stackId, Boolean role, String permission) {
        this.clusterId = clusterId;
        this.stackId = stackId;
        this.role = role;
        this.permission = permission;
    }

    @Override
    public String getAuthority() {
        if (authority != null) {
            return authority;
        }
        StringBuilder sb = new StringBuilder();
        if (role != null && role) {
            sb.append(StringConstant.ROLE_PREFIX);
        }
        if (clusterId != null) {
            sb.append("cluster").append(StringConstant.UNDER_LINE)
                    .append(clusterId)
                    .append(StringConstant.UNDER_LINE);
        }
        if (stackId != null) {
            sb.append("stack").append(StringConstant.UNDER_LINE)
                    .append(stackId)
                    .append(StringConstant.UNDER_LINE);
        }
        sb.append(permission);
        authority = sb.toString();
        return authority;
    }
}
