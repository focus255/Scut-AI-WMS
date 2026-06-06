/**
 * 后台用户管理服务实现，负责用户查询、创建、改密与角色分配。
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.AssignUserRolesRequest;
import com.smartwms.dto.RoleSimpleVO;
import com.smartwms.dto.UpdatePasswordRequest;
import com.smartwms.dto.UserAdminVO;
import com.smartwms.dto.UserCreateRequest;
import com.smartwms.dto.UserUpdateRequest;
import com.smartwms.entity.Role;
import com.smartwms.entity.User;
import com.smartwms.entity.UserRole;
import com.smartwms.mapper.RoleMapper;
import com.smartwms.mapper.UserMapper;
import com.smartwms.mapper.UserRoleMapper;
import com.smartwms.service.UserAdminService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserAdminServiceImpl(UserMapper userMapper,
                                RoleMapper roleMapper,
                                UserRoleMapper userRoleMapper,
                                BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询后台用户，并组装角色信息供管理端展示。
     */
    @Override
    public Page<UserAdminVO> page(int current, int size, String keyword) {
        Page<User> userPage = new Page<>(current, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        Page<UserAdminVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toUserAdminVO).toList());
        return voPage;
    }

    /**
     * 根据用户 ID 查询后台用户详情。
     */
    @Override
    public UserAdminVO getById(Long userId) {
        return toUserAdminVO(getUserOrThrow(userId));
    }

    /**
     * 创建后台用户，并在创建后同步写入用户角色关系。
     */
    @Override
    public void create(UserCreateRequest request) {
        validateStatus(request.getStatus());
        ensureUsernameUnique(request.getUsername(), null);
        List<Long> roleIds = validateRoleIds(request.getRoleIds());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(request.getStatus());
        userMapper.insert(user);

        replaceUserRoles(user.getUserId(), roleIds);
    }

    /**
     * 更新后台用户的基础信息，不处理账号名与角色关系。
     */
    @Override
    public void update(Long userId, UserUpdateRequest request) {
        User user = getUserOrThrow(userId);
        validateStatus(request.getStatus());
        user.setNickname(request.getNickname());
        user.setStatus(request.getStatus());
        userMapper.updateById(user);
    }

    /**
     * 重置指定用户密码。
     */
    @Override
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        User user = getUserOrThrow(userId);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userMapper.updateById(user);
    }

    /**
     * 重新分配用户角色，采用全量覆盖方式更新关系表。
     */
    @Override
    public void assignRoles(Long userId, AssignUserRolesRequest request) {
        getUserOrThrow(userId);
        replaceUserRoles(userId, validateRoleIds(request.getRoleIds()));
    }

    /**
     * 查询用户，不存在时抛出业务异常。
     */
    private User getUserOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 校验账号名是否已被占用，更新场景可排除当前用户。
     */
    private void ensureUsernameUnique(String username, Long excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        if (excludeUserId != null) {
            wrapper.ne(User::getUserId, excludeUserId);
        }
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号已存在");
        }
    }

    /**
     * 当前版本仅允许启用和禁用两种用户状态。
     */
    private void validateStatus(String status) {
        if (!STATUS_ENABLED.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户状态不合法");
        }
    }

    /**
     * 去重后校验角色 ID 是否全部有效。
     */
    private List<Long> validateRoleIds(List<Long> roleIds) {
        List<Long> distinctRoleIds = roleIds.stream().distinct().toList();
        List<Role> roles = roleMapper.selectBatchIds(distinctRoleIds);
        if (roles.size() != distinctRoleIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的角色ID");
        }
        return distinctRoleIds;
    }

    /**
     * 采用先删后插的方式全量覆盖用户角色关系，保证提交结果与请求保持一致。
     */
    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    /**
     * 将用户实体转换为后台管理端使用的展示对象。
     */
    private UserAdminVO toUserAdminVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setStatus(user.getStatus());
        vo.setRoles(loadRoles(user.getUserId()));
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }

    private List<RoleSimpleVO> loadRoles(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        );
        if (userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).distinct().toList();
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(role -> new RoleSimpleVO(role.getId(), role.getRoleCode(), role.getRoleName()))
                .toList();
    }
}
