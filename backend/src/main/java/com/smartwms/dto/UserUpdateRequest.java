package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(max = 100, message = "昵称长度不能超过 100 个字符")
    private String nickname;

    @NotBlank(message = "状态不能为空")
    private String status;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
