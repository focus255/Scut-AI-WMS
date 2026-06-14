package com.smartwms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.OutboundHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出库批次流水 Mapper。
 */
@Mapper
public interface OutboundHistoryMapper extends BaseMapper<OutboundHistory> {
}
