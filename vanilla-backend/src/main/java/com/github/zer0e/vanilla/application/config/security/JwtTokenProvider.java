package com.github.zer0e.vanilla.application.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析：subject 为登录用户名，HS256 签名
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${vanilla.jwt.secret}")
    private String secret;

    @Value("${vanilla.jwt.expiration-minutes:720}")
    private long expirationMinutes;

    private SecretKey key;

    @PostConstruct
    void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMinutes * 60_000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 token 获取用户名；token 非法/过期抛异常
     */
    public String getUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean isValid(String token) {
        try {
            getUsername(token);
            return true;
        } catch (Exception e) {
            log.debug("invalid jwt: {}", e.getMessage());
            return false;
        }
    }
}