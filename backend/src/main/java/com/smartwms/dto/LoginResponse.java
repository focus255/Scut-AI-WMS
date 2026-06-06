/**
 * 登录响应 DTO。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.dto;

import java.util.ArrayList;
import java.util.List;

public class LoginResponse {

    private String token;
    private long expiresIn;
    private String username;
    private List<String> roles = new ArrayList<>();

    public LoginResponse() {}

    public LoginResponse(String token, long expiresIn, String username, List<String> roles) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.username = username;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
}
