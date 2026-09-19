package com.febrie.demo_bk.identity.infrastructure.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil =
            new JwtUtil("test-jwt-secret-change-me-at-least-32-bytes");

    @Test
    void generateTokenShouldContainJwtId() {
        // 新签发的 JWT 必须带 jti，退出登录时才能只废除当前 Token。
        String token = jwtUtil.generateToken("admin");
        Claims claims = jwtUtil.parsePayload(token);

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(jwtUtil.isTokenExpired(claims)).isFalse();
        assertThat(jwtUtil.getRemainingMillis(claims)).isPositive();
    }
}
