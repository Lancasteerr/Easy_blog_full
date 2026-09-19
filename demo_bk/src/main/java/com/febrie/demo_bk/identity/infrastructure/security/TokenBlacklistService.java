package com.febrie.demo_bk.identity.infrastructure.security;

import com.febrie.demo_bk.shared.infrastructure.redis.RedisStore;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Service
public class TokenBlacklistService {

    private static final String JTI_KEY_PREFIX = "blog:auth:jwt:blacklist:jti:";

    private static final String TOKEN_HASH_KEY_PREFIX = "blog:auth:jwt:blacklist:sha256:";

    private final RedisStore redisStore;

    private final JwtUtil jwtUtil;

    public TokenBlacklistService(RedisStore redisStore,
                                 JwtUtil jwtUtil) {
        this.redisStore = redisStore;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 废除当前JWT，并让黑名单记录随JWT原本的过期时间自动清理。
     */
    public void revoke(String token) {
        Claims claims = jwtUtil.parsePayload(token);
        long remainingMillis = jwtUtil.getRemainingMillis(claims);

        if (remainingMillis <= 0) {
            return;
        }

        redisStore.set(
                buildBlacklistKey(token, claims),
                "1",
                remainingMillis,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * 判断JWT是否已被主动注销。
     */
    public boolean isRevoked(String token,
                             Claims claims) {
        return redisStore.get(buildBlacklistKey(token, claims)) != null;
    }

    private String buildBlacklistKey(String token,
                                     Claims claims) {
        String jwtId = claims.getId();

        if (jwtId != null && !jwtId.isBlank()) {
            return JTI_KEY_PREFIX + jwtId;
        }

        // 兼容历史上没有jti的JWT，避免把完整token明文写入Redis。
        return TOKEN_HASH_KEY_PREFIX + sha256(token);
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JDK不支持SHA-256摘要算法", e);
        }
    }
}
