/**
 * 跨域配置，允许前端 Vite 开发服务器 (5173) 访问后端 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许前端开发和生产环境来源
        config.setAllowedOriginPatterns(List.of("*"));
        // 允许携带认证头
        config.setAllowCredentials(true);
        // 允许所有请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许所有请求方法
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 预检缓存 1 小时
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
