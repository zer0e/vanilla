package com.github.zer0e.vanilla.application.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器：在 @PreAuthorize 之前把登录用户名装载进 SecurityContext。
 * 优先解析 {@code Authorization: Bearer <token>}；无 token 时兜底兼容旧 {@code x-auth-user} 请求头
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    /**
     * 兼容旧调用方的请求头（过渡期兜底，后续可移除）
     */
    private static final String LEGACY_AUTH_HEADER = "x-auth-user";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String principal = resolvePrincipal(request);
        if (principal != null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(principal);
                if (userDetails != null) {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                logger.warn("load user for auth err, principal=" + principal, e);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 提取登录用户名：优先 JWT subject，其次兼容旧 x-auth-user 头
     */
    private String resolvePrincipal(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (StringUtils.hasText(token)) {
                try {
                    return jwtTokenProvider.getUsername(token);
                } catch (Exception e) {
                    logger.warn("invalid bearer token, skip auth");
                    return null;
                }
            }
        }
        return request.getHeader(LEGACY_AUTH_HEADER);
    }
}