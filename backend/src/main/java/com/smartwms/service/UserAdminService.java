package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.AssignUserRolesRequest;
import com.smartwms.dto.UpdatePasswordRequest;
import com.smartwms.dto.UserAdminVO;
import com.smartwms.dto.UserCreateRequest;
import com.smartwms.dto.UserUpdateRequest;

public interface UserAdminService {

    Page<UserAdminVO> page(int current, int size, String keyword);

    UserAdminVO getById(Long userId);

    void create(UserCreateRequest request);

    void update(Long userId, UserUpdateRequest request);

    void updatePassword(Long userId, UpdatePasswordRequest request);

    void assignRoles(Long userId, AssignUserRolesRequest request);
}
