/**
 * 后台用户管理控制器，提供用户查询、编辑、改密和角色分配接口。
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.AssignUserRolesRequest;
import com.smartwms.dto.UpdatePasswordRequest;
import com.smartwms.dto.UserAdminVO;
import com.smartwms.dto.UserCreateRequest;
import com.smartwms.dto.UserUpdateRequest;
import com.smartwms.service.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 分页查询后台用户列表。
     */
    @GetMapping
    public Result<Page<UserAdminVO>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) String keyword) {
        return Result.success(userAdminService.page(page, size, keyword));
    }

    /**
     * 查询指定用户详情。
     */
    @GetMapping("/{id}")
    public Result<UserAdminVO> getById(@PathVariable Long id) {
        return Result.success(userAdminService.getById(id));
    }

    /**
     * 新增后台用户。
     */
    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserCreateRequest request) {
        userAdminService.create(request);
        return Result.success("用户创建成功", null);
    }

    /**
     * 更新后台用户基础信息。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userAdminService.update(id, request);
        return Result.success("用户更新成功", null);
    }

    /**
     * 重置后台用户密码。
     */
    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(@PathVariable Long id, @Valid @RequestBody UpdatePasswordRequest request) {
        userAdminService.updatePassword(id, request);
        return Result.success("密码重置成功", null);
    }

    /**
     * 重新分配后台用户角色。
     */
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignUserRolesRequest request) {
        userAdminService.assignRoles(id, request);
        return Result.success("角色分配成功", null);
    }
}
