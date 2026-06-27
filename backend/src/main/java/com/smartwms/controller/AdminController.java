/**
 * 管理维护接口 — 数据库清理与诊断。
 *
 * @author Focus
 * @date 2026-06-28
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.common.Result;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.InventoryFreeze;
import com.smartwms.entity.OutboundDetail;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.mapper.InventoryFreezeMapper;
import com.smartwms.mapper.OutboundDetailMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.mapper.OutboundOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final BarcodeMapper barcodeMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final OutboundDetailMapper outboundDetailMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final InventoryFreezeMapper inventoryFreezeMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InboundOrderMapper inboundOrderMapper;

    public AdminController(BarcodeMapper barcodeMapper,
                           OutboundHistoryMapper outboundHistoryMapper,
                           OutboundDetailMapper outboundDetailMapper,
                           OutboundOrderMapper outboundOrderMapper,
                           InventoryFreezeMapper inventoryFreezeMapper,
                           InboundDetailMapper inboundDetailMapper,
                           InboundOrderMapper inboundOrderMapper) {
        this.barcodeMapper = barcodeMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.outboundDetailMapper = outboundDetailMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.inventoryFreezeMapper = inventoryFreezeMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inboundOrderMapper = inboundOrderMapper;
    }

    /**
     * 诊断：全面检查条码、入库单号、出库单号格式是否符合规范。
     * GET /api/admin/diagnose-barcodes
     *
     * 规范：
     *   条码:   WMS|物料|供应商|计划数|箱容量|实收数|箱序号 (7段，箱序号无前导零)
     *   入库单: RK + yyyyMMdd + 4位序号 (共14字符)
     *   出库单: CK + yyyyMMdd + 5位序号 (共15字符)
     */
    @GetMapping("/diagnose-barcodes")
    public Result<Map<String, Object>> diagnoseBarcodes() {
        List<Barcode> allBarcodes = barcodeMapper.selectList(null);
        List<String> badBarcodes = new ArrayList<>();
        List<String> badInboundOrders = new ArrayList<>();
        List<String> badOutboundOrders = new ArrayList<>();
        Set<Long> badBarcodeIds = new HashSet<>();
        Set<Long> badInboundIds = new HashSet<>();

        // 条码规范：7段（6个|），箱序号不含前导零
        for (Barcode bc : allBarcodes) {
            String b = bc.getBarcode();
            if (b == null) continue;
            int pipes = b.length() - b.replace("|", "").length();
            boolean isBad = false;
            // 不是正好6个| = 不是7段
            if (pipes != 6) isBad = true;
            // 箱序号含非法字符（如 0001-3 范围格式，或非数字非_S后缀）
            if (!isBad && b.matches(".*\\|.*[-].*$")) isBad = true;     // 范围格式 0001-3
            if (!isBad && !b.matches(".*\\|[0-9]+(_S[0-9]+)?$")) isBad = true;
            if (isBad) {
                badBarcodes.add(b);
                badBarcodeIds.add(bc.getId());
                badInboundIds.add(bc.getInboundId());
            }
        }

        // 入库单号规范：RK + 8位日期 + 4位序号 = 14字符
        List<InboundOrder> inOrders = inboundOrderMapper.selectList(null);
        for (InboundOrder o : inOrders) {
            String no = o.getOrderNo();
            if (no == null) continue;
            if (!no.matches("^RK\\d{12}$")) {
                badInboundOrders.add(no);
                badInboundIds.add(o.getId());
            }
        }

        // 出库单号规范：CK + 8位日期 + 5位序号 = 15字符
        List<OutboundOrder> outOrders = outboundOrderMapper.selectList(null);
        Set<Long> badOutboundIds = new HashSet<>();
        for (OutboundOrder o : outOrders) {
            String no = o.getOrderNo();
            if (no == null) continue;
            if (!no.matches("^CK\\d{13}$")) {
                badOutboundOrders.add(no);
                badOutboundIds.add(o.getId());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBarcodes", allBarcodes.size());
        result.put("badBarcodeCount", badBarcodes.size());
        result.put("badBarcodes", badBarcodes);
        result.put("badInboundOrderCount", badInboundOrders.size());
        result.put("badInboundOrders", badInboundOrders);
        result.put("badOutboundOrderCount", badOutboundOrders.size());
        result.put("badOutboundOrders", badOutboundOrders);

        log.info("[管理诊断] 违规条码={} 违规入库单={} 违规出库单={}",
            badBarcodes.size(), badInboundOrders.size(), badOutboundOrders.size());
        return Result.success(result);
    }

    /**
     * 清理：删除所有格式违规的条码、入库单、出库单及其关联数据。
     * DELETE /api/admin/cleanup-barcodes
     */
    @DeleteMapping("/cleanup-barcodes")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> cleanupBarcodes() {
        Set<Long> badBarcodeIds = new HashSet<>();
        Set<Long> badInboundIds = new HashSet<>();
        Set<Long> badOutboundIds = new HashSet<>();
        int badBarcodeCount = 0, badInOrderCount = 0, badOutOrderCount = 0;

        // 1. 收集违规条码（非7段 或 箱序号含前导零/非法字符）
        List<Barcode> allBarcodes = barcodeMapper.selectList(null);
        for (Barcode bc : allBarcodes) {
            String b = bc.getBarcode();
            if (b == null) continue;
            int pipes = b.length() - b.replace("|", "").length();
            boolean isBad = (pipes != 6);
            if (!isBad && b.matches(".*\\|.*[-].*$")) isBad = true;     // 范围格式 0001-3
            if (!isBad && !b.matches(".*\\|[0-9]+(_S[0-9]+)?$")) isBad = true; // 箱序号非数字非_S
            if (isBad) {
                badBarcodeCount++;
                badBarcodeIds.add(bc.getId());
                badInboundIds.add(bc.getInboundId());
            }
        }

        // 2. 收集违规入库单（非 RK + 12 位数字）
        List<InboundOrder> inOrders = inboundOrderMapper.selectList(null);
        for (InboundOrder o : inOrders) {
            String no = o.getOrderNo();
            if (no != null && !no.matches("^RK\\d{12}$")) {
                badInOrderCount++;
                badInboundIds.add(o.getId());
            }
        }

        // 3. 收集违规出库单（非 CK + 13 位数字）
        List<OutboundOrder> outOrders = outboundOrderMapper.selectList(null);
        for (OutboundOrder o : outOrders) {
            String no = o.getOrderNo();
            if (no != null && !no.matches("^CK\\d{13}$")) {
                badOutOrderCount++;
                badOutboundIds.add(o.getId());
            }
        }

        if (badBarcodeIds.isEmpty() && badInboundIds.isEmpty() && badOutboundIds.isEmpty()) {
            return Result.success(Map.of("message", "无异常数据，无需清理"));
        }

        // 4. 收集涉及的出库单（通过条码关联的流水）
        if (!badBarcodeIds.isEmpty()) {
            List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>().in(OutboundHistory::getBarcodeId, badBarcodeIds));
            for (OutboundHistory h : histories) badOutboundIds.add(h.getOutboundId());
        }

        int delHistories = 0, delOutDetails = 0, delOutOrders = 0;
        int delFreezes = 0, delBarcodes = 0, delInDetails = 0, delInOrders = 0;

        // 出库流水
        if (!badBarcodeIds.isEmpty()) delHistories = outboundHistoryMapper.delete(
            new LambdaQueryWrapper<OutboundHistory>().in(OutboundHistory::getBarcodeId, badBarcodeIds));
        // 出库明细 + 出库单
        if (!badOutboundIds.isEmpty()) {
            delOutDetails = outboundDetailMapper.delete(
                new LambdaQueryWrapper<OutboundDetail>().in(OutboundDetail::getOutboundId, badOutboundIds));
            delOutOrders = outboundOrderMapper.deleteBatchIds(badOutboundIds);
        }
        // 封存
        if (!badBarcodeIds.isEmpty()) delFreezes = inventoryFreezeMapper.delete(
            new LambdaQueryWrapper<InventoryFreeze>().in(InventoryFreeze::getBarcodeId, badBarcodeIds));
        // 条码
        if (!badBarcodeIds.isEmpty()) delBarcodes = barcodeMapper.deleteBatchIds(badBarcodeIds);
        // 入库明细
        if (!badInboundIds.isEmpty()) delInDetails = inboundDetailMapper.delete(
            new LambdaQueryWrapper<InboundDetail>().in(InboundDetail::getInboundId, badInboundIds));
        // 入库单（仅删除已无明细的）
        if (!badInboundIds.isEmpty()) {
            for (Long id : badInboundIds) {
                if (inboundDetailMapper.selectCount(
                    new LambdaQueryWrapper<InboundDetail>().eq(InboundDetail::getInboundId, id)) == 0) {
                    inboundOrderMapper.deleteById(id);
                    delInOrders++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "清理完成");
        result.put("badBarcodes", badBarcodeCount);
        result.put("badInboundOrders", badInOrderCount);
        result.put("badOutboundOrders", badOutOrderCount);
        result.put("deletedBarcodes", delBarcodes);
        result.put("deletedInboundOrders", delInOrders);
        result.put("deletedOutboundOrders", delOutOrders);
        result.put("deletedFreezes", delFreezes);
        result.put("deletedHistories", delHistories);

        log.info("[管理清理] 条码={} 入库单={} 出库单={} | 删除条码={} 入库={} 出库={}",
            badBarcodeCount, badInOrderCount, badOutOrderCount, delBarcodes, delInOrders, delOutOrders);
        return Result.success(result);
    }

    /**
     * 标准化看板号第7段（箱序号）：移除前导零。
     * PUT /api/admin/normalize-barcodes
     */
    @PutMapping("/normalize-barcodes")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> normalizeBarcodes() {
        List<Barcode> allBarcodes = barcodeMapper.selectList(null);
        int fixed = 0;
        for (Barcode bc : allBarcodes) {
            String b = bc.getBarcode();
            if (b == null || !b.startsWith("WMS|")) continue;
            String[] parts = b.split("\\|");
            if (parts.length != 7) continue;
            String boxSeq = parts[6];
            // 纯数字且长度>1且有前导零的才需要修复
            if (boxSeq.matches("0[0-9]+")) {
                String normalized = boxSeq.replaceFirst("^0+", "");
                parts[6] = normalized;
                String newBarcode = String.join("|", parts);
                bc.setBarcode(newBarcode);
                barcodeMapper.updateById(bc);
                fixed++;
            }
        }
        Map<String, Object> result = Map.of("message", "标准化完成", "fixedCount", fixed);
        log.info("[管理标准化] 修复箱序号前导零: {} 条", fixed);
        return Result.success(result);
    }
}
