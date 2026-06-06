package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.AssignRolePermissionsRequest;
import com.smartwms.dto.RoleCreateRequest;
import com.smartwms.dto.RoleUpdateRequest;
import com.smartwms.dto.RoleVO;

public interface RoleService {

    Page<RoleVO> page(int current, int size, String keyword);

    RoleVO getById(Long id);

    void create(RoleCreateRequest request);

    void update(Long id, RoleUpdateRequest request);

    void assignPermissions(Long id, AssignRolePermissionsRequest request);

    void delete(Long id);
}
