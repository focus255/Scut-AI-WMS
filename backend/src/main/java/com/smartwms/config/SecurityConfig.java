/**
 * 安全加密配置，提供 BCryptPasswordEncoder Bean。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {

    /**
     * BCrypt 密码编码器，用于用户密码的加密存储与校验。
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
