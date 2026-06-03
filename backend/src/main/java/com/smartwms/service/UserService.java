/**
 * 用户认证服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.smartwms.dto.LoginRequest;
import com.smartwms.dto.LoginResponse;
import com.smartwms.dto.RegisterRequest;

public interface UserService {

    /**
     * 用户注册。
     *
     * @param request 注册请求（账号 + 明文密码）
     * @throws com.smartwms.common.BusinessException 账号已存在时抛出
     */
    void register(RegisterRequest request);

    /**
     * 用户登录认证。
     *
     * @param request 登录请求（账号 + 明文密码）
     * @return 登录响应（JWT Token + 有效期）
     * @throws com.smartwms.common.BusinessException 凭据错误时抛出
     */
    LoginResponse login(LoginRequest request);
}
