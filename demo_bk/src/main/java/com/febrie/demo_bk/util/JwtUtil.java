package com.febrie.demo_bk.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecureDigestAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT生成和解析
 */

@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * JWT签名密钥只从运行时配置读取，避免把生产密钥提交到仓库。
     */
    private final SecretKey key;

    //加密算法
    private final SecureDigestAlgorithm<SecretKey,SecretKey> algorithm = Jwts.SIG.HS256;

    public JwtUtil(@Value("${blog.jwt.secret:}") String secret) {
        byte[] keyBytes = resolveSecretBytes(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    //生成JWT
    public String generateToken(String username){
        return Jwts.builder()
                // jti用于唯一标识本次签发的JWT，退出登录时可精确废除当前token。
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000))
                .signWith(key,algorithm)
                .compact();
    }

    /**
     * 解析token
     * @param token token
     * @return Jws<Claims>
     */
    public Jws<Claims> parseToken(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    //获取Payload
    public Claims parsePayload(String token){
        return parseToken(token).getPayload();
    }

    //获取用户信息
    public String getUsernameFromToken(String token){
        return parsePayload(token).getSubject();
    }

    //判断token是否过期
    public boolean isTokenExpired(String token) {
        return isTokenExpired(parsePayload(token));
    }

    //判断已解析的Claims是否过期，避免过滤器重复解析token。
    public boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration == null || expiration.before(new Date());//比较过期时间
    }

    //获取token距离过期还剩多少毫秒，用于设置Redis黑名单自动过期时间。
    public long getRemainingMillis(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return 0;
        }

        return Math.max(0, expiration.getTime() - System.currentTimeMillis());
    }

    private byte[] resolveSecretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "BLOG_JWT_SECRET不能为空，请在环境变量或.env中配置至少32字节的随机密钥"
            );
        }

        String trimmedSecret = secret.trim();
        byte[] decodedBytes = tryDecodeBase64(trimmedSecret);
        byte[] rawBytes = trimmedSecret.getBytes(StandardCharsets.UTF_8);

        // 优先使用Base64解码结果，兼容推荐的随机密钥生成方式；普通字符串也可作为本地开发密钥。
        if (decodedBytes != null && decodedBytes.length >= MIN_SECRET_BYTES) {
            return decodedBytes;
        }

        if (rawBytes.length >= MIN_SECRET_BYTES) {
            return rawBytes;
        }

        throw new IllegalStateException(
                "BLOG_JWT_SECRET强度不足，HS256至少需要32字节随机密钥"
        );
    }

    private byte[] tryDecodeBase64(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
