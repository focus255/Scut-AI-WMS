/**
 * 用户认证服务实现。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.common.JwtUtil;
import com.smartwms.dto.LoginRequest;
import com.smartwms.dto.LoginResponse;
import com.smartwms.dto.RegisterRequest;
import com.smartwms.entity.User;
import com.smartwms.mapper.UserMapper;
import com.smartwms.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void register(RegisterRequest request) {
        // 校验账号是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            log.warn("[注册失败] 账号已存在: {}", request.getUsername());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该账号已被注册，请更换账号");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userMapper.insert(user);

        log.info("[注册成功] 新用户: {} (userId={})", request.getUsername(), user.getUserId());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            log.warn("[登录失败] 账号不存在: {}", request.getUsername());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[登录失败] 密码错误: {}", request.getUsername());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        // 生成 JWT
        String token = JwtUtil.generateToken(user.getUserId());
        log.info("[Auth] 用户账号 {} 成功登录系统，生成JWT，有效载荷顺延{}分钟",
                user.getUsername(), JwtUtil.getExpirationMs() / 60000);

        return new LoginResponse(token, JwtUtil.getExpirationMs() / 1000);
    }
}
