/**
 * 角色管理服务实现，负责角色维护与角色权限关系管理。
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.AssignRolePermissionsRequest;
import com.smartwms.dto.PermissionSimpleVO;
import com.smartwms.dto.RoleCreateRequest;
import com.smartwms.dto.RoleUpdateRequest;
import com.smartwms.dto.RoleVO;
import com.smartwms.entity.Permission;
import com.smartwms.entity.Role;
import com.smartwms.entity.RolePermission;
import com.smartwms.entity.UserRole;
import com.smartwms.mapper.PermissionMapper;
import com.smartwms.mapper.RoleMapper;
import com.smartwms.mapper.RolePermissionMapper;
import com.smartwms.mapper.UserRoleMapper;
import com.smartwms.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleServiceImpl(RoleMapper roleMapper,
                           PermissionMapper permissionMapper,
                           RolePermissionMapper rolePermissionMapper,
                           UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 分页查询角色，并同时返回角色下挂载的权限信息。
     */
    @Override
    public Page<RoleVO> page(int current, int size, String keyword) {
        Page<Role> rolePage = new Page<>(current, size);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Role::getRoleCode, keyword)
                    .or()
                    .like(Role::getRoleName, keyword);
        }
        wrapper.orderByDesc(Role::getCreatedAt);
        Page<Role> result = roleMapper.selectPage(rolePage, wrapper);

        Page<RoleVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toRoleVO).toList());
        return voPage;
    }

    /**
     * 根据角色 ID 查询角色详情。
     */
    @Override
    public RoleVO getById(Long id) {
        return toRoleVO(getRoleOrThrow(id));
    }

    /**
     * 创建角色，角色编码在系统内必须唯一。
     */
    @Override
    public void create(RoleCreateRequest request) {
        ensureRoleCodeUnique(request.getRoleCode(), null);
        Role role = new Role();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setRemark(request.getRemark());
        roleMapper.insert(role);
    }

    /**
     * 更新角色基础信息，不修改角色编码与权限关系。
     */
    @Override
    public void update(Long id, RoleUpdateRequest request) {
        Role role = getRoleOrThrow(id);
        role.setRoleName(request.getRoleName());
        role.setRemark(request.getRemark());
        roleMapper.updateById(role);
    }

    /**
     * 重新分配角色权限，采用全量覆盖关系表的方式保存。
     */
    @Override
    public void assignPermissions(Long id, AssignRolePermissionsRequest request) {
        getRoleOrThrow(id);
        replaceRolePermissions(id, validatePermissionIds(request.getPermissionIds()));
    }

    /**
     * 删除角色前先校验是否仍被用户引用，避免产生悬挂关系。
     */
    @Override
    public void delete(Long id) {
        getRoleOrThrow(id);
        long userRefs = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        if (userRefs > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法删除：该角色已分配给用户");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    /**
     * 查询角色，不存在时抛出业务异常。
     */
    private Role getRoleOrThrow(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    /**
     * 校验角色编码唯一性，更新场景可排除当前角色。
     */
    private void ensureRoleCodeUnique(String roleCode, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        if (roleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色编码已存在");
        }
    }

    /**
     * 去重后校验权限 ID 是否全部有效。
     */
    private List<Long> validatePermissionIds(List<Long> permissionIds) {
        List<Long> distinctPermissionIds = permissionIds.stream().distinct().toList();
        List<Permission> permissions = permissionMapper.selectBatchIds(distinctPermissionIds);
        if (permissions.size() != distinctPermissionIds.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在无效的权限ID");
        }
        return distinctPermissionIds;
    }

    /**
     * 采用先删后插的方式覆盖角色权限关系，避免出现旧权限残留。
     */
    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    /**
     * 将角色实体转换为包含权限信息的展示对象。
     */
    private RoleVO toRoleVO(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setRemark(role.getRemark());
        vo.setPermissions(loadPermissions(role.getId()));
        vo.setCreatedAt(role.getCreatedAt());
        vo.setUpdatedAt(role.getUpdatedAt());
        return vo;
    }

    private List<PermissionSimpleVO> loadPermissions(Long roleId) {
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId)
        );
        if (rolePermissions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = rolePermissions.stream().map(RolePermission::getPermissionId).distinct().toList();
        return permissionMapper.selectBatchIds(permissionIds).stream()
                .map(permission -> new PermissionSimpleVO(
                        permission.getId(),
                        permission.getPermissionCode(),
                        permission.getPermissionName()))
                .toList();
    }
}
