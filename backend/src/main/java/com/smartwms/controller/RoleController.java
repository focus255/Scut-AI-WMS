/**
 * 角色管理控制器，提供角色维护与权限分配接口。
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.AssignRolePermissionsRequest;
import com.smartwms.dto.RoleCreateRequest;
import com.smartwms.dto.RoleUpdateRequest;
import com.smartwms.dto.RoleVO;
import com.smartwms.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * 分页查询角色列表。
     */
    @GetMapping
    public Result<Page<RoleVO>> page(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String keyword) {
        return Result.success(roleService.page(page, size, keyword));
    }

    /**
     * 查询指定角色详情。
     */
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    /**
     * 新增角色。
     */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody RoleCreateRequest request) {
        roleService.create(request);
        return Result.success("角色创建成功", null);
    }

    /**
     * 更新角色基础信息。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        roleService.update(id, request);
        return Result.success("角色更新成功", null);
    }

    /**
     * 重新分配角色权限。
     */
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody AssignRolePermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return Result.success("权限分配成功", null);
    }

    /**
     * 删除指定角色。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success("角色删除成功", null);
    }
}
