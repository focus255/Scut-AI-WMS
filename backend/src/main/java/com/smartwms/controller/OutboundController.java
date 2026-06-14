package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出库管理控制器。
 */
@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    /**
     * 分页查询出库单。
     */
    @GetMapping("/orders")
    public Result<Page<OutboundOrder>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.success(outboundService.page(page, size));
    }

    /**
     * 查询出库单详情。
     */
    @GetMapping("/orders/{id}")
    public Result<OutboundOrderVO> getById(@PathVariable Long id) {
        return Result.success(outboundService.getById(id));
    }

    /**
     * 创建出库单。
     */
    @PostMapping("/orders")
    public Result<OutboundOrder> create(@Valid @RequestBody OutboundOrderRequest request) {
        return Result.success("出库单创建成功", outboundService.create(request));
    }

    /**
     * 确认出库。
     */
    @PutMapping("/orders/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id,
                                @Valid @RequestBody ConfirmOutboundRequest request) {
        outboundService.confirm(id, request);
        return Result.success("出库确认成功", null);
    }

    /**
     * 分页查询出库批次流水。
     */
    @GetMapping("/histories")
    public Result<Page<OutboundHistoryVO>> pageHistories(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String orderNo,
                                                         @RequestParam(required = false) String materialCode) {
        return Result.success(outboundService.pageHistories(page, size, orderNo, materialCode));
    }
}
