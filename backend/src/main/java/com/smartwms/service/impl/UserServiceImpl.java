/**
 * 用户认证服务实现，负责注册、登录以及默认角色装配。
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.common.JwtUtil;
import com.smartwms.dto.LoginRequest;
import com.smartwms.dto.LoginResponse;
import com.smartwms.dto.RegisterRequest;
import com.smartwms.entity.Role;
import com.smartwms.entity.User;
import com.smartwms.entity.UserRole;
import com.smartwms.mapper.RoleMapper;
import com.smartwms.mapper.UserMapper;
import com.smartwms.mapper.UserRoleMapper;
import com.smartwms.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String ROLE_OPERATOR = "OPERATOR";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper,
                           RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper,
                           BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户，并默认分配操作员角色。
     */
    @Override
    public void register(RegisterRequest request) {
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
        user.setNickname(request.getUsername());
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);
        assignDefaultRole(user.getUserId());

        log.info("[注册成功] 新用户: {} (userId={})", request.getUsername(), user.getUserId());
    }

    /**
     * 校验账号与密码，并在登录成功后把角色信息写入 JWT。
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            log.warn("[登录失败] 账号不存在: {}", request.getUsername());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("[登录失败] 密码错误: {}", request.getUsername());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名或密码错误");
        }

        if (!STATUS_ENABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        List<String> roles = loadRoleCodes(user.getUserId());
        String token = JwtUtil.generateToken(user.getUserId(), user.getUsername(), roles);
        log.info("[Auth] 用户账号 {} 成功登录系统，生成JWT，有效载荷顺延{}分钟",
                user.getUsername(), JwtUtil.getExpirationMs() / 60000);

        return new LoginResponse(token, JwtUtil.getExpirationMs() / 1000, user.getUsername(), roles);
    }

    /**
     * 普通注册用户默认授予 OPERATOR 角色，保证具备最小可访问能力。
     */
    private void assignDefaultRole(Long userId) {
        Role operatorRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, ROLE_OPERATOR)
        );
        if (operatorRole == null) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(operatorRole.getId());
        userRoleMapper.insert(userRole);
    }

    /**
     * 加载用户拥有的角色编码列表，用于登录响应与 JWT 生成。
     */
    private List<String> loadRoleCodes(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getRoleCode).toList();
    }
}
