package com.github.zer0e.vanilla.application.config.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签发/解析：往返一致、过期拒绝、篡改拒绝
 */
class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(provider, "secret",
                "vanilla-8f3a2c6e-9b41-4f7d-8c2a-5e6d7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f");
        ReflectionTestUtils.setField(provider, "expirationMinutes", 720L);
        provider.init();
    }

    @Test
    void generateAndParse_roundTrip() {
        String token = provider.generateToken("admin");

        assertThat(token).isNotBlank();
        assertThat(provider.getUsername(token)).isEqualTo("admin");
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void expiredToken_isRejected() {
        // 过期为负分钟 → 立即过期
        ReflectionTestUtils.setField(provider, "expirationMinutes", -1L);
        String token = provider.generateToken("admin");

        assertThat(provider.isValid(token)).isFalse();
        assertThatThrownBy(() -> provider.getUsername(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tamperedToken_isRejected() {
        String token = provider.generateToken("admin");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(provider.isValid(tampered)).isFalse();
    }
}