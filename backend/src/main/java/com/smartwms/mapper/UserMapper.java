/**
 * 用户表 Mapper 接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
