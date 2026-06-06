package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class UserCreateRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 50, message = "账号长度需在 3-50 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在 6-100 个字符之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 100, message = "昵称长度不能超过 100 个字符")
    private String nickname;

    @NotBlank(message = "状态不能为空")
    private String status;

    @NotEmpty(message = "至少分配一个角色")
    private List<Long> roleIds = new ArrayList<>();

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
