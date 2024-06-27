package com.github.zer0e.vanilla.application.config.security;

import com.github.zer0e.vanilla.infrastructure.db.mapper.UserMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserDo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDo userDo = userMapper.findByLoginName(username);
        if (userDo == null) {
            return null;
        }
        // TODO roles and authorities
        return User.withUsername(username)
                .password("")
                .roles("USER")
                .build();
    }
}
