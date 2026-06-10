/**
 * JWT 配置桥接类，将 application.yml 中的 jwt.secret / jwt.expiration-ms
 * 注入到静态工具类 JwtUtil，实现密钥外部化。
 *
 * @author Focus
 * @date 2026-06-10
 */
package com.smartwms.config;

import com.smartwms.common.JwtUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * 启动时将配置值注入 JwtUtil 静态字段。
     */
    @PostConstruct
    public void initJwtUtil() {
        JwtUtil.setSecret(secret);
        JwtUtil.setExpirationMs(expirationMs);
    }
}
