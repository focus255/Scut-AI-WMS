package com.smartwms.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String remark;
    private List<PermissionSimpleVO> permissions = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public List<PermissionSimpleVO> getPermissions() { return permissions; }
    public void setPermissions(List<PermissionSimpleVO> permissions) { this.permissions = permissions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
