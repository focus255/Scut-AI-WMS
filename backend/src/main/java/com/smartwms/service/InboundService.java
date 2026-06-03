/**
 * 入库单服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.entity.InboundOrder;

public interface InboundService {

    /**
     * 创建入库单（生成唯一单号、拆分条码）。
     */
    InboundOrder create(InboundOrderRequest request);

    /**
     * 分页查询入库单列表。
     */
    Page<InboundOrder> page(int current, int size);

    /**
     * 手工确认入库（核销明细数量、增加库存、更新条码状态）。
     */
    void confirm(Long inboundId);
}
