/**
 * 角色权限分配请求。
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class AssignRolePermissionsRequest {

    /**
     * 需要挂载到角色上的权限 ID 列表。
     */
    @NotEmpty(message = "至少分配一个权限")
    private List<Long> permissionIds = new ArrayList<>();

    public List<Long> getPermissionIds() { return permissionIds; }
    public void setPermissionIds(List<Long> permissionIds) { this.permissionIds = permissionIds; }
}
