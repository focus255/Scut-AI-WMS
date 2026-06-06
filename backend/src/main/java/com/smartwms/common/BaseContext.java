/**
 * 线程本地上下文，用于在请求生命周期中传递当前登录用户信息。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class BaseContext {

    private static final ThreadLocal<LoginUserContext> CURRENT_USER = new ThreadLocal<>();

    private BaseContext() {}

    /**
     * 设置当前请求上下文中的用户 ID。
     */
    public static void setCurrentUser(Long userId, String username, Set<String> roleCodes) {
        CURRENT_USER.set(new LoginUserContext(userId, username, roleCodes));
    }

    /**
     * 获取当前请求上下文中的用户 ID。
     *
     * @return 当前用户 ID，如果未设置则返回 null
     */
    public static Long getCurrentId() {
        LoginUserContext context = CURRENT_USER.get();
        return context == null ? null : context.userId();
    }

    public static String getCurrentUsername() {
        LoginUserContext context = CURRENT_USER.get();
        return context == null ? null : context.username();
    }

    public static Set<String> getCurrentRoles() {
        LoginUserContext context = CURRENT_USER.get();
        return context == null ? Collections.emptySet() : context.roleCodes();
    }

    /**
     * 请求处理完毕后清除上下文，防止内存泄漏。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }

    private record LoginUserContext(Long userId, String username, Set<String> roleCodes) {
        private LoginUserContext {
            roleCodes = Collections.unmodifiableSet(new LinkedHashSet<>(roleCodes));
        }
    }
}
