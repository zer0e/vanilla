package com.github.zer0e.vanilla.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 独立于 SecurityConfig 的 Bean 定义，避免 SecurityConfig → JwtFilter → UserService → PasswordEncoder → SecurityConfig 循环依赖
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}