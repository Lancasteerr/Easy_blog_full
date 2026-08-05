package com.febrie.demo_bk.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil("test-jwt-secret-change-me-at-least-32-bytes");

    @Test
    void generateTokenShouldContainJwtId() {
        // 新签发的JWT必须带jti，退出登录时才能只废除当前token。
        String token = jwtUtil.generateToken("admin");
        Claims claims = jwtUtil.parsePayload(token);

        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(jwtUtil.isTokenExpired(claims)).isFalse();
        assertThat(jwtUtil.getRemainingMillis(claims)).isPositive();
    }
}
