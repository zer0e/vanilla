package com.github.zer0e.vanilla.domain;

import com.github.zer0e.vanilla.infrastructure.db.repository.UserDo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User extends UserDo implements UserDetails {

    private List<UserRolePermission> authorities;

    @Override
    public boolean isEnabled() {
        return this.getStatus() == null || this.getStatus() == UserStatus.ENABLED.ordinal();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return getLoginName();
    }
}
