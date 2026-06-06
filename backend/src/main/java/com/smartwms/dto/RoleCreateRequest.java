/**
 * 新增角色请求。
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RoleCreateRequest {

    /**
     * 角色编码，作为角色的唯一业务标识。
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 100, message = "角色编码长度不能超过 100 个字符")
    private String roleCode;

    /**
     * 角色名称，用于管理端展示。
     */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过 100 个字符")
    private String roleName;

    /**
     * 角色备注，用于补充说明角色职责。
     */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
