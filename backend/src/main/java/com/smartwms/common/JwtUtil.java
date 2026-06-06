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
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class JwtUtil {

    /**
     * JWT 签名密钥（生产环境应从外部配置注入）。
     */
    private static final String SECRET = "smart-wms-jwt-secret-key-2026-must-be-at-least-256-bits!!";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 令牌有效期：2 小时（毫秒） */
    private static final long EXPIRATION_MS = 2 * 60 * 60 * 1000L;
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";

    private JwtUtil() {}

    public static String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + EXPIRATION_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
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
        return EXPIRATION_MS;
    }
}
