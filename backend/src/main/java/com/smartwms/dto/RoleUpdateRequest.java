package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RoleUpdateRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过 100 个字符")
    private String roleName;

    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    private String remark;

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
