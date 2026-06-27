/**
 * 入库历史查询控制器，提供日期范围筛选与汇总统计。
 *
 * @author Claude
 * @date 2026-06-15
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.InboundOrderVO;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.service.InboundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inbound")
public class InboundHistoryController {

    private static final Logger log = LoggerFactory.getLogger(InboundHistoryController.class);

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final BarcodeMapper barcodeMapper;
    private final InboundService inboundService;

    public InboundHistoryController(InboundOrderMapper inboundOrderMapper,
                                     InboundDetailMapper inboundDetailMapper,
                                     BarcodeMapper barcodeMapper,
                                     InboundService inboundService) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.barcodeMapper = barcodeMapper;
        this.inboundService = inboundService;
    }

    /**
     * 查询入库历史（支持日期范围、状态、关键字筛选）。
     * GET /api/inbound/history?page=1&size=10&startDate=2026-06-01&endDate=2026-06-15&status=已完成&keyword=RK
     *
     * @return { records, total, summary: { totalBatches, totalQty, dailyTrend: [...] } }
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();

        // 日期范围筛选
        if (startDate != null) {
            wrapper.ge(InboundOrder::getCreatedAt, LocalDateTime.of(startDate, LocalTime.MIN));
        }
        if (endDate != null) {
            wrapper.le(InboundOrder::getCreatedAt, LocalDateTime.of(endDate, LocalTime.MAX));
        }
        // 状态筛选
        if (status != null && !status.isBlank()) {
            wrapper.eq(InboundOrder::getStatus, status.trim());
        }
        // 关键字：匹配单号或供应商
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(InboundOrder::getOrderNo, kw)
                    .or().like(InboundOrder::getSupplierCode, kw));
        }
        wrapper.orderByDesc(InboundOrder::getCreatedAt);

        Page<InboundOrder> orderPage = inboundOrderMapper.selectPage(new Page<>(page, size), wrapper);

        // 汇总统计：查询当前筛选条件下的全部数据（不分页）
        LambdaQueryWrapper<InboundOrder> summaryWrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            summaryWrapper.ge(InboundOrder::getCreatedAt, LocalDateTime.of(startDate, LocalTime.MIN));
        }
        if (endDate != null) {
            summaryWrapper.le(InboundOrder::getCreatedAt, LocalDateTime.of(endDate, LocalTime.MAX));
        }
        if (status != null && !status.isBlank()) {
            summaryWrapper.eq(InboundOrder::getStatus, status.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            summaryWrapper.and(w -> w.like(InboundOrder::getOrderNo, kw)
                    .or().like(InboundOrder::getSupplierCode, kw));
        }
        List<InboundOrder> allOrders = inboundOrderMapper.selectList(summaryWrapper);

        // 总入库件数：汇总已完成订单的明细实际入库数
        int totalQty = 0;
        List<Long> completedIds = allOrders.stream()
                .filter(o -> "已完成".equals(o.getStatus()))
                .map(InboundOrder::getId)
                .toList();
        if (!completedIds.isEmpty()) {
            totalQty = inboundDetailMapper.selectList(
                    new LambdaQueryWrapper<InboundDetail>().in(InboundDetail::getInboundId, completedIds)
            ).stream().mapToInt(d -> d.getActualQty() != null ? d.getActualQty() : 0).sum();
        }

        // 近7天每日入库趋势
        Map<String, Integer> dailyTrend = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dailyTrend.put(day.toString(), 0);
        }
        for (InboundOrder o : allOrders) {
            if ("已完成".equals(o.getStatus()) && o.getCreatedAt() != null) {
                String dayKey = o.getCreatedAt().toLocalDate().toString();
                if (dailyTrend.containsKey(dayKey)) {
                    // 查询该订单的明细总入库数
                    int orderQty = inboundDetailMapper.selectList(
                            new LambdaQueryWrapper<InboundDetail>().eq(InboundDetail::getInboundId, o.getId())
                    ).stream().mapToInt(d -> d.getActualQty() != null ? d.getActualQty() : 0).sum();
                    dailyTrend.merge(dayKey, orderQty, Integer::sum);
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalBatches", allOrders.size());
        summary.put("totalQty", totalQty);
        summary.put("dailyTrend", dailyTrend.entrySet().stream()
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", e.getKey());
                    item.put("qty", e.getValue());
                    return item;
                }).toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", orderPage.getRecords());
        result.put("total", orderPage.getTotal());
        result.put("summary", summary);
        return Result.success(result);
    }

    /**
     * 查询入库流水（按二维码维度），支持按入库单号和物料号筛选。
     * GET /api/inbound/flow?page=1&size=10&orderNo=&materialCode=
     *
     * @return 分页的二维码记录，含关联入库单号
     */
    @GetMapping("/flow")
    public Result<Map<String, Object>> flow(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode) {

        log.info("[入库流水] 查询 page={} size={} orderNo={} materialCode={}", page, size, orderNo, materialCode);
        LambdaQueryWrapper<Barcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Barcode::getType, "inbound");

        if (orderNo != null && !orderNo.isBlank()) {
            // 通过入库单号找到对应 inbound_id
            List<Long> inboundIds = inboundOrderMapper.selectList(
                    new LambdaQueryWrapper<InboundOrder>()
                            .like(InboundOrder::getOrderNo, orderNo.trim())
            ).stream().map(InboundOrder::getId).toList();

            if (inboundIds.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("records", List.of());
                result.put("total", 0L);
                return Result.success(result);
            }
            wrapper.in(Barcode::getInboundId, inboundIds);
        }

        if (materialCode != null && !materialCode.isBlank()) {
            wrapper.like(Barcode::getMaterialCode, materialCode.trim());
        }

        wrapper.orderByDesc(Barcode::getCreatedAt);

        Page<Barcode> barcodePage = barcodeMapper.selectPage(new Page<>(page, size), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", barcodePage.getRecords());
        result.put("total", barcodePage.getTotal());
        return Result.success(result);
    }
}
