package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.entity.OutboundOrder;

/**
 * 出库服务接口。
 */
public interface OutboundService {

    OutboundOrder create(OutboundOrderRequest request);

    Page<OutboundOrder> page(int current, int size);

    OutboundOrderVO getById(Long id);

    void confirm(Long outboundId, ConfirmOutboundRequest request);

    Page<OutboundHistoryVO> pageHistories(int current, int size, String orderNo, String materialCode);
}
