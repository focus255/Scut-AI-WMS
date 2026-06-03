/**
 * 用户认证控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.smartwms.common.Result;
import com.smartwms.dto.LoginRequest;
import com.smartwms.dto.LoginResponse;
import com.smartwms.dto.RegisterRequest;
import com.smartwms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 用户注册。
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success("注册成功，请使用新账号登录", null);
    }

    /**
     * 用户登录。
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }
}
