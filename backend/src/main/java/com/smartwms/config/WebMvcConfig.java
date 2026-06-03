/**
 * Spring MVC 配置，注册 JWT 鉴权拦截器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.smartwms.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")                     // 拦截所有 API 请求
                .excludePathPatterns(
                        "/api/auth/login",        // 放行登录接口
                        "/api/auth/register"      // 放行注册接口
                );
    }
}
