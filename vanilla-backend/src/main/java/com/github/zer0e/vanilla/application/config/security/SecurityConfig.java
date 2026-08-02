package com.github.zer0e.vanilla.application.config.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NO_PERMISSION_MSG;

    private static final String NO_LOGIN_MSG;

    static {
        try {
            NO_LOGIN_MSG = objectMapper.writeValueAsString(
                    RestResponse.fail(HttpStatus.UNAUTHORIZED.value(),
                            HttpStatus.UNAUTHORIZED.getReasonPhrase())
            );
            NO_PERMISSION_MSG = objectMapper.writeValueAsString(
                    RestResponse.fail(HttpStatus.FORBIDDEN.value(),
                            Constants.NO_PERMISSION
                    ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private final JwtAuthenticationFilter jwtAuthenticationFilter;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .exceptionHandling(httpSecurityExceptionHandlingConfigurer -> {
                            httpSecurityExceptionHandlingConfigurer.authenticationEntryPoint(
                                    (request, response, authException) -> {
                                        response.setStatus(HttpStatus.OK.value());
                                        response.setContentType(Constants.DEFAULT_CONTENT_TYPE);
                                        response.getWriter().write(NO_LOGIN_MSG);
                                    }
                            );
                            httpSecurityExceptionHandlingConfigurer.accessDeniedHandler((request, response, accessDeniedException) -> {
                                response.setStatus(HttpStatus.OK.value());
                                response.setContentType(Constants.DEFAULT_CONTENT_TYPE);
                                response.getWriter().write(NO_PERMISSION_MSG);
                            });
                        }
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/v3/api-docs/**", "/webjars/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/doc.html",
                                "/error",
                                "/auth/api/v1/login")
                        .permitAll()
                        .anyRequest().authenticated()

                )
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
        ;
        return http.build();
    }
}
