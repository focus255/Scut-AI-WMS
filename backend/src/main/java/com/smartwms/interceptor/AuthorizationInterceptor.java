/**
 * 权限拦截器，用于在 JWT 认证通过后补充基于角色的接口访问控制。
 */
package com.smartwms.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwms.common.BaseContext;
import com.smartwms.common.ErrorCode;
import com.smartwms.common.Result;
import com.smartwms.entity.Permission;
import com.smartwms.entity.Role;
import com.smartwms.entity.RolePermission;
import com.smartwms.mapper.PermissionMapper;
import com.smartwms.mapper.RoleMapper;
import com.smartwms.mapper.RolePermissionMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public AuthorizationInterceptor(RoleMapper roleMapper,
                                    PermissionMapper permissionMapper,
                                    RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 根据当前登录用户的角色信息校验接口访问权限。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String permission = resolveRequiredPermission(request.getRequestURI(), request.getMethod());
        if (permission == null) {
            return true;
        }

        Set<String> roleCodes = BaseContext.getCurrentRoles();
        // 管理员拥有全部后台权限，不再继续查询角色权限关系。
        if (roleCodes.contains("ADMIN")) {
            return true;
        }
        if (roleCodes.isEmpty()) {
            writeForbidden(response, "当前账号没有访问权限");
            return false;
        }

        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().in(Role::getRoleCode, roleCodes));
        if (roles.isEmpty()) {
            writeForbidden(response, "当前账号没有访问权限");
            return false;
        }

        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds)
        );
        if (rolePermissions.isEmpty()) {
            writeForbidden(response, "当前账号没有访问权限");
            return false;
        }

        List<Long> permissionIds = rolePermissions.stream().map(RolePermission::getPermissionId).distinct().toList();
        Set<String> permissionCodes = permissionMapper.selectBatchIds(permissionIds).stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());

        if (!permissionCodes.contains(permission)) {
            writeForbidden(response, "当前账号没有访问权限");
            return false;
        }
        return true;
    }

    /**
     * 根据路径前缀和请求方法映射当前请求所需的权限编码。
     */
    private String resolveRequiredPermission(String uri, String method) {
        if (uri.startsWith("/api/suppliers")) {
            return "GET".equalsIgnoreCase(method) ? "supplier:read" : "supplier:write";
        }
        if (uri.startsWith("/api/admin/users")) {
            return "GET".equalsIgnoreCase(method) ? "user:read" : "user:write";
        }
        if (uri.startsWith("/api/roles")) {
            return "GET".equalsIgnoreCase(method) ? "role:read" : "role:write";
        }
        return null;
    }

    /**
     * 以统一响应结构返回 403 无权限结果。
     */
    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error(ErrorCode.FORBIDDEN, message);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(result));
    }
}
