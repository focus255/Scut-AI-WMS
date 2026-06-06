/**
 * 用户角色分配请求。
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class AssignUserRolesRequest {

    /**
     * 需要分配给用户的角色 ID 列表。
     */
    @NotEmpty(message = "至少分配一个角色")
    private List<Long> roleIds = new ArrayList<>();

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
