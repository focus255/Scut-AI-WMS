/**
 * 入库单服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.ConfirmInboundRequest;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.dto.InboundOrderVO;
import com.smartwms.dto.InventoryTraceVO;
import com.smartwms.dto.ScanInboundRequest;
import com.smartwms.dto.ScanInboundVO;
import com.smartwms.entity.InboundOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface InboundService {

    /**
     * 创建入库单（生成唯一单号、拆分二维码）。
     */
    InboundOrder create(InboundOrderRequest request);

    /**
     * 批量创建入库单（同一事务，全部成功或全部回滚）。
     */
    List<InboundOrder> batchCreate(List<InboundOrderRequest> requests);

    /**
     * 分页查询入库单列表。
     */
    Page<InboundOrder> page(int current, int size);

    Page<InboundOrder> page(int current, int size, String status, String keyword, LocalDate startDate, LocalDate endDate);

    /**
     * 查询入库单详情（含明细行）。
     */
    InboundOrderVO getById(Long id);

    /**
     * 修改入库单（仅"未完成"状态可修改）。
     */
    InboundOrder update(Long id, InboundOrderRequest request);

    /**
     * 手工确认入库（核销明细数量、增加库存、更新二维码状态）。
     *
     * @param inboundId 入库单主键 ID
     * @param request   确认请求（含每行实际入库数量）；为 null 时默认按计划数全量入库
     */
    void confirm(Long inboundId, ConfirmInboundRequest request);

    /**
     * 扫码入库：按看板号精确核销单箱入库。
     */
    ScanInboundVO scanReceive(ScanInboundRequest request);

    /**
     * 删除入库单（仅"未完成"状态可删除）。
     */
    void delete(Long id);

    /**
     * 库存追溯：按物料/二维码/入库单号查询二维码生命周期轨迹。
     */
    InventoryTraceVO trace(String materialCode, String barcode, String orderNo, int page, int size);

    /**
     * 入库单摘要统计（全局，不受分页影响）。
     * 返回 totalBatches / totalQty / pendingCount / completedCount。
     */
    Map<String, Object> summary(String status, String keyword, LocalDate startDate, LocalDate endDate);
}
