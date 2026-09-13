package com.febrie.demo_bk.identity.internal;

import com.febrie.demo_bk.shared.infrastructure.RedisStore;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private static final long TWELVE_HOURS_MILLIS = 12 * 60 * 60 * 1000L;

    @Mock
    private RedisStore redisStore;

    private JwtUtil jwtUtil;
    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        // 使用固定密钥构造 JWT 工具，避免单元测试依赖 Spring 上下文。
        jwtUtil = new JwtUtil("test-jwt-secret-change-me-at-least-32-bytes");
        tokenBlacklistService = new TokenBlacklistService(redisStore, jwtUtil);
    }

    @Test
    void revokeShouldStoreJtiBlacklistEntryWithRemainingTtl() {
        String token = jwtUtil.generateToken("admin");
        Claims claims = jwtUtil.parsePayload(token);

        tokenBlacklistService.revoke(token);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisStore).set(
                keyCaptor.capture(),
                eq("1"),
                ttlCaptor.capture(),
                eq(TimeUnit.MILLISECONDS)
        );

        assertThat(keyCaptor.getValue())
                .startsWith("blog:auth:jwt:blacklist:jti:")
                .endsWith(claims.getId());
        assertThat(ttlCaptor.getValue())
                .isPositive()
                .isLessThanOrEqualTo(TWELVE_HOURS_MILLIS);
    }

    @Test
    void isRevokedShouldReturnTrueWhenRedisHasBlacklistEntry() {
        String token = jwtUtil.generateToken("admin");
        Claims claims = jwtUtil.parsePayload(token);
        when(redisStore.get(anyString())).thenReturn("1");

        assertThat(tokenBlacklistService.isRevoked(token, claims)).isTrue();
    }

    @Test
    void isRevokedShouldUseSha256KeyForLegacyTokenWithoutJti() {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(null);
        when(redisStore.get(anyString())).thenReturn(null);

        assertThat(tokenBlacklistService.isRevoked("legacy-token", claims)).isFalse();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisStore).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("blog:auth:jwt:blacklist:sha256:");
        assertThat(keyCaptor.getValue()).doesNotContain("legacy-token");
        assertThat(keyCaptor.getValue().replace("blog:auth:jwt:blacklist:sha256:", ""))
                .hasSize(64);
    }
}
