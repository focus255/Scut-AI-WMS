package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.dto.ScanResponse;
import com.smartwms.entity.OutboundOrder;

import java.time.LocalDate;

/**
 * 出库服务接口。
 */
public interface OutboundService {

    OutboundOrder create(OutboundOrderRequest request);

    Page<OutboundOrder> page(int current, int size);

    Page<OutboundOrder> page(int current, int size, String status, String orderNo, LocalDate startDate, LocalDate endDate);

    OutboundOrderVO getById(Long id);

    void confirm(Long outboundId, ConfirmOutboundRequest request);

    Page<OutboundHistoryVO> pageHistories(int current, int size, String orderNo, String materialCode);

    /**
     * 扫码出库：解析出库标签条码，按 FIFO 选取仓库条码并核销。
     * @param barcodeStr 出库标签条码字符串
     * @return 统一扫码响应
     */
    ScanResponse scanOutbound(String barcodeStr);

    /**
     * 修改出库单（仅"未出库"/"部分出库"状态可修改）。
     * 修改会退回已拣库存并重新执行拆零拣选。
     */
    void update(Long id, OutboundOrderRequest request);

    /**
     * 删除出库单（仅"未出库"状态可删除，退回已拣库存）。
     */
    void delete(Long id);
}
