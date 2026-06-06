package com.smartwms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
