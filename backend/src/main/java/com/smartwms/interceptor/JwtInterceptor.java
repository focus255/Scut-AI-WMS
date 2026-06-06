/**
 * JWT 鉴权拦截器，对每个 API 请求进行 Token 合法性校验。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwms.common.BaseContext;
import com.smartwms.common.ErrorCode;
import com.smartwms.common.JwtUtil;
import com.smartwms.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验 Bearer Token，并把当前登录用户上下文写入线程变量。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        // 检查 Authorization 头是否存在且符合 Bearer 前缀
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[鉴权拦截] 请求缺少合法Authorization头, URI={}", request.getRequestURI());
            writeUnauthorized(response, "未提供有效的认证凭证");
            return false;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = JwtUtil.getUserId(claims);
            String username = JwtUtil.getUsername(claims);
            List<String> roles = JwtUtil.getRoles(claims);
            BaseContext.setCurrentUser(userId, username, new LinkedHashSet<>(roles));
            log.info("[鉴权放行] userId={}, URI={}", userId, request.getRequestURI());
            return true;
        } catch (JwtException e) {
            log.warn("[鉴权拦截] JWT无效或已过期, URI={}, error={}", request.getRequestURI(), e.getMessage());
            writeUnauthorized(response, "认证凭证无效或已过期，请重新登录");
            return false;
        }
    }

    /**
     * 请求完成后清理线程上下文，避免线程复用导致数据串用。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求处理完毕后清除上下文，防止内存泄漏
        BaseContext.clear();
    }

    /**
     * 以统一响应结构返回 401 未认证结果。
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(ErrorCode.UNAUTHORIZED, message);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
