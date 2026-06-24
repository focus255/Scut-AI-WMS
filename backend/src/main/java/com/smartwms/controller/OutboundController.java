package com.smartwms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.dto.ScanInboundRequest;
import com.smartwms.dto.ScanResponse;
import com.smartwms.dto.ScanInboundVO;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.service.InboundService;
import com.smartwms.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 出库管理及统一扫码控制器。
 */
@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;
    private final InboundService inboundService;
    private final BarcodeMapper barcodeMapper;

    public OutboundController(OutboundService outboundService,
                              InboundService inboundService,
                              BarcodeMapper barcodeMapper) {
        this.outboundService = outboundService;
        this.inboundService = inboundService;
        this.barcodeMapper = barcodeMapper;
    }

    /**
     * 分页查询出库单。
     */
    @GetMapping("/orders")
    public Result<Page<OutboundOrder>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String orderNo,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(outboundService.page(page, size, status, orderNo, startDate, endDate));
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

    /**
     * 修改出库单（仅"未出库"或"部分出库"状态可修改）。
     */
    @PutMapping("/orders/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody OutboundOrderRequest request) {
        outboundService.update(id, request);
        return Result.success("出库单修改成功", null);
    }

    /**
     * 删除出库单（仅"未出库"状态可删除，已拣货的库存会退回）。
     */
    @DeleteMapping("/orders/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        outboundService.delete(id);
        return Result.success("出库单删除成功", null);
    }

    /**
     * 统一扫码入口：自动判定二维码类型（入库/出库）并执行对应操作。
     * 出库标签格式: OUT|<materialCode>|<outboundOrderNo>|<packCapacity>|<planQty>|<boxQty>|<boxSeq>
     * 入库二维码格式: WMS|<materialCode>|<supplierCode>|<planQty>|<packCapacity>|<actualQty>|<boxSeq>
     */
    /** 出库专用扫码（WMS二维码，仅接受待出库状态） */
    @PostMapping("/scan/wms")
    public Result<ScanResponse> scanOutboundWms(@Valid @RequestBody ScanInboundRequest request) {
        return Result.success("扫码出库成功", outboundService.scanOutbound(request.getBarcode().trim()));
    }

    @PostMapping("/scan")
    public Result<ScanResponse> unifiedScan(@Valid @RequestBody ScanInboundRequest request) {
        String barcode = request.getBarcode().trim();
        if (barcode.startsWith("OUT|")) {
            return Result.success("扫码出库成功", outboundService.scanOutbound(barcode));
        }
        // WMS 二维码：按状态分派。待出库→出库核销，其他→入库核销
        Barcode bc = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>().eq(Barcode::getBarcode, barcode)
        );
        if (bc != null && "待出库".equals(bc.getStatus())) {
            return Result.success("扫码出库成功", outboundService.scanOutbound(barcode));
        }
        ScanInboundVO inboundResult = inboundService.scanReceive(request);
        return Result.success("扫码入库成功", ScanResponse.inbound(inboundResult));
    }
}
