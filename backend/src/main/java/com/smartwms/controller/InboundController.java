/**
 * 入库单控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.InboundOrderRequest;
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
     * 新建入库单。
     * POST /api/inbound/orders
     */
    @PostMapping("/orders")
    public Result<InboundOrder> create(@Valid @RequestBody InboundOrderRequest request) {
        InboundOrder order = inboundService.create(request);
        return Result.success("入库单创建成功", order);
    }

    /**
     * 手工确认入库。
     * PUT /api/inbound/orders/{id}/confirm
     */
    @PutMapping("/orders/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        inboundService.confirm(id);
        return Result.success("入库确认成功", null);
    }
}
