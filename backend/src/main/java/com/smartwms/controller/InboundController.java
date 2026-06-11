/**
 * 入库单控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.ConfirmInboundRequest;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.dto.InboundOrderVO;
import com.smartwms.dto.InventoryTraceVO;
import com.smartwms.dto.ScanInboundRequest;
import com.smartwms.dto.ScanInboundVO;
import com.smartwms.entity.InboundOrder;
import com.smartwms.service.InboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inbound")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    /**
     * 分页查询入库单列表。
     * GET /api/inbound/orders?page=1&size=10
     */
    @GetMapping("/orders")
    public Result<Page<InboundOrder>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(inboundService.page(page, size));
    }

    /**
     * 查询入库单详情（含明细行）。
     * GET /api/inbound/orders/{id}
     */
    @GetMapping("/orders/{id}")
    public Result<InboundOrderVO> getById(@PathVariable Long id) {
        return Result.success(inboundService.getById(id));
    }

    /**
     * 新建入库单。
     * POST /api/inbound/orders
     */
    @PostMapping("/orders")
    public Result<InboundOrder> create(@Valid @RequestBody InboundOrderRequest request) {
        InboundOrder order = inboundService.create(request);
        return Result.success("入库单创建成功", order);
    }

    /**
     * 修改入库单（仅"未入库"状态可修改）。
     * PUT /api/inbound/orders/{id}
     */
    @PutMapping("/orders/{id}")
    public Result<InboundOrder> update(@PathVariable Long id,
                                       @Valid @RequestBody InboundOrderRequest request) {
        InboundOrder order = inboundService.update(id, request);
        return Result.success("入库单修改成功", order);
    }

    /**
     * 手工确认入库，支持按明细行传入实际到货数量。
     * PUT /api/inbound/orders/{id}/confirm
     * 请求体可选：不传或传空 details 时默认按计划数全量入库。
     */
    @PutMapping("/orders/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id,
                                @RequestBody(required = false) ConfirmInboundRequest request) {
        inboundService.confirm(id, request);
        return Result.success("入库确认成功", null);
    }

    /**
     * 扫码入库：按条码号精确核销单箱入库。
     * POST /api/inbound/scan
     */
    @PostMapping("/scan")
    public Result<ScanInboundVO> scanReceive(@Valid @RequestBody ScanInboundRequest request) {
        return Result.success("扫码入库成功", inboundService.scanReceive(request));
    }

    /**
     * 库存追溯：按物料/条码/入库单号查询条码生命周期轨迹。
     * GET /api/inbound/trace?materialCode=&barcode=&orderNo=
     */
    @GetMapping("/trace")
    public Result<InventoryTraceVO> trace(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) String orderNo) {
        return Result.success(inboundService.trace(materialCode, barcode, orderNo));
    }
}
