package com.smartwms.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserAdminVO {

    private Long userId;
    private String username;
    private String nickname;
    private String status;
    private List<RoleSimpleVO> roles = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<RoleSimpleVO> getRoles() { return roles; }
    public void setRoles(List<RoleSimpleVO> roles) { this.roles = roles; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
