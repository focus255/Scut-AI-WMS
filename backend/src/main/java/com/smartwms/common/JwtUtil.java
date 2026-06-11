/**
 * JWT 令牌工具类，负责令牌的生成与解析校验。
 * 密钥和过期时间由 JwtConfig 在启动时注入，支持通过 application.yml 或环境变量外部化配置。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class JwtUtil {

    /** 默认密钥（开发环境使用，生产环境由 JwtConfig 注入覆盖） */
    private static String secret = "smart-wms-jwt-secret-key-2026-must-be-at-least-256-bits!!";
    private static volatile SecretKey secretKey = buildKey(secret);

    /** 令牌有效期：默认 2 小时（毫秒） */
    private static long expirationMs = 2 * 60 * 60 * 1000L;

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";

    private JwtUtil() {}

    /**
     * 由 JwtConfig 在启动时调用，注入外部化配置的密钥。
     *
     * @param newSecret 新密钥字符串
     */
    public static void setSecret(String newSecret) {
        if (newSecret != null && !newSecret.isEmpty()) {
            secret = newSecret;
            secretKey = buildKey(newSecret);
        }
    }

    /**
     * 由 JwtConfig 在启动时调用，注入外部化配置的过期时间。
     *
     * @param ms 过期时间（毫秒）
     */
    public static void setExpirationMs(long ms) {
        if (ms > 0) {
            expirationMs = ms;
        }
    }

    private static SecretKey buildKey(String key) {
        return Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public static Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof List<?> roleList) {
            return roleList.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    public static long getExpirationMs() {
        return expirationMs;
    }
}
