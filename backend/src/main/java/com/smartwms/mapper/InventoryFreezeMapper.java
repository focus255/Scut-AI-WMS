/**
 * 库存封存表 Mapper 接口。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.InventoryFreeze;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryFreezeMapper extends BaseMapper<InventoryFreeze> {
}
