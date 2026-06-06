/**
 * Spring MVC 配置，注册 JWT 鉴权拦截器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.smartwms.interceptor.AuthorizationInterceptor;
import com.smartwms.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor,
                        AuthorizationInterceptor authorizationInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register"
                );

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register"
                );
    }
}
