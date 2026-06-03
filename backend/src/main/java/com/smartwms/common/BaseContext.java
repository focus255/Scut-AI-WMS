/**
 * 线程本地上下文，用于在请求生命周期中传递当前登录用户 ID。
 * 配合 JwtInterceptor 使用，在拦截器中注入，在 Service/Mapper 层读取。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

public class BaseContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private BaseContext() {}

    /**
     * 设置当前请求上下文中的用户 ID。
     */
    public static void setCurrentId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    /**
     * 获取当前请求上下文中的用户 ID。
     *
     * @return 当前用户 ID，如果未设置则返回 null
     */
    public static Long getCurrentId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * 请求处理完毕后清除上下文，防止内存泄漏。
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
