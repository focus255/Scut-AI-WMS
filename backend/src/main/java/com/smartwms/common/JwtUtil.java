/**
 * JWT 令牌工具类，负责令牌的生成与解析校验。
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
import java.util.Date;

public class JwtUtil {

    /**
     * JWT 签名密钥（生产环境应从外部配置注入）。
     */
    private static final String SECRET = "smart-wms-jwt-secret-key-2026-must-be-at-least-256-bits!!";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 令牌有效期：2 小时（毫秒） */
    private static final long EXPIRATION_MS = 2 * 60 * 60 * 1000L;

    private JwtUtil() {}

    /**
     * 生成 JWT 令牌。
     *
     * @param userId 用户主键 ID
     * @return 签发的 JWT 字符串
     */
    public static String generateToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * 解析并校验 JWT 令牌，提取用户 ID。
     *
     * @param token JWT 令牌字符串
     * @return 用户 ID
     * @throws JwtException 令牌无效或过期时抛出
     */
    public static Long parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 获取令牌过期时间（毫秒）。
     */
    public static long getExpirationMs() {
        return EXPIRATION_MS;
    }
}
