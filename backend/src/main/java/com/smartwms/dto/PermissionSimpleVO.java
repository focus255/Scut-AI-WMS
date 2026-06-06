package com.smartwms.dto;

public class PermissionSimpleVO {

    private Long id;
    private String permissionCode;
    private String permissionName;

    public PermissionSimpleVO() {}

    public PermissionSimpleVO(Long id, String permissionCode, String permissionName) {
        this.id = id;
        this.permissionCode = permissionCode;
        this.permissionName = permissionName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
}
