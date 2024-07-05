package com.github.zer0e.vanilla.application.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class XAuthUserFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;

    private static final String AUTH_HEADER_NAME = "x-auth-user";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String principal = request.getHeader(AUTH_HEADER_NAME);

        if (principal != null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(principal);
                if (userDetails != null) {
                    Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails,
                            null,
                            userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }catch (Exception e) {
                logger.warn("get user detail err", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}
