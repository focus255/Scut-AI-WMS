package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdatePasswordRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度需在 6-100 个字符之间")
    private String password;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
