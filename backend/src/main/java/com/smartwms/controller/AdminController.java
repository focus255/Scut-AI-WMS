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
     * 诊断：查询所有 8 段格式的条码及其关联数据。
     * GET /api/admin/diagnose-barcodes
     */
    @GetMapping("/diagnose-barcodes")
    public Result<Map<String, Object>> diagnoseBarcodes() {
        List<Barcode> all = barcodeMapper.selectList(null);
        List<String> bad8Seg = new ArrayList<>();
        List<String> oldSplit = new ArrayList<>();
        Set<Long> badBarcodeIds = new HashSet<>();
        Set<Long> badInboundIds = new HashSet<>();

        for (Barcode bc : all) {
            String b = bc.getBarcode();
            if (b == null) continue;
            // 8 段格式：含 8 个 | 分隔的段（WMS|...|...|...|...|...|...|...）
            int pipes = b.length() - b.replace("|", "").length();
            if (pipes >= 7) {
                bad8Seg.add(b);
                badBarcodeIds.add(bc.getId());
                badInboundIds.add(bc.getInboundId());
            }
            // 旧拆分格式：_S 不在最后一段内
            if (b.contains("_S") && !b.matches(".*\\|[^|]*_S[^|]*$")) {
                oldSplit.add(b);
                badBarcodeIds.add(bc.getId());
                badInboundIds.add(bc.getInboundId());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBarcodes", all.size());
        result.put("bad8SegmentCount", bad8Seg.size());
        result.put("bad8SegmentBarcodes", bad8Seg);
        result.put("oldSplitCount", oldSplit.size());
        result.put("oldSplitBarcodes", oldSplit);
        result.put("affectedBarcodeIds", badBarcodeIds.size());
        result.put("affectedInboundIds", badInboundIds.size());

        // 统计关联数据
        if (!badBarcodeIds.isEmpty()) {
            Long freezeCount = inventoryFreezeMapper.selectCount(
                new LambdaQueryWrapper<InventoryFreeze>().in(InventoryFreeze::getBarcodeId, badBarcodeIds));
            Long historyCount = outboundHistoryMapper.selectCount(
                new LambdaQueryWrapper<OutboundHistory>().in(OutboundHistory::getBarcodeId, badBarcodeIds));
            result.put("relatedFreezes", freezeCount);
            result.put("relatedOutboundHistories", historyCount);
        }

        log.info("[管理诊断] 8段条码={} 个, 旧拆分={} 个, 涉及入库单={} 个",
            bad8Seg.size(), oldSplit.size(), badInboundIds.size());
        return Result.success(result);
    }

    /**
     * 清理：删除所有 8 段格式和旧拆分格式的条码及其关联数据。
     * DELETE /api/admin/cleanup-barcodes
     *
     * 删除顺序（遵守外键依赖）：
     *   出库流水 → 出库明细 → 出库单 → 封存记录 → 条码 → 入库明细 → 入库单
     */
    @DeleteMapping("/cleanup-barcodes")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> cleanupBarcodes() {
        // 1. 收集所有异常条码
        List<Barcode> allBarcodes = barcodeMapper.selectList(null);
        Set<Long> badBarcodeIds = new HashSet<>();
        Set<Long> badInboundIds = new HashSet<>();
        Set<Long> badOutboundIds = new HashSet<>();
        int bad8Count = 0, oldSplitCount = 0;

        for (Barcode bc : allBarcodes) {
            String b = bc.getBarcode();
            if (b == null) continue;
            boolean isBad = false;
            int pipes = b.length() - b.replace("|", "").length();
            if (pipes >= 7) { bad8Count++; isBad = true; }
            if (b.contains("_S") && !b.matches(".*\\|[^|]*_S[^|]*$")) { oldSplitCount++; isBad = true; }
            if (isBad) {
                badBarcodeIds.add(bc.getId());
                badInboundIds.add(bc.getInboundId());
            }
        }

        if (badBarcodeIds.isEmpty()) {
            return Result.success(Map.of("message", "无异常条码，无需清理"));
        }

        // 2. 收集涉及的出库单 ID
        if (!badBarcodeIds.isEmpty()) {
            List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>().in(OutboundHistory::getBarcodeId, badBarcodeIds));
            for (OutboundHistory h : histories) {
                badOutboundIds.add(h.getOutboundId());
            }
        }

        // 3. 按外键依赖顺序删除
        int deletedHistories = 0, deletedOutDetails = 0, deletedOutOrders = 0;
        int deletedFreezes = 0, deletedBarcodes = 0, deletedInDetails = 0, deletedInOrders = 0;

        // 3a. 出库流水
        if (!badBarcodeIds.isEmpty()) {
            deletedHistories = outboundHistoryMapper.delete(
                new LambdaQueryWrapper<OutboundHistory>().in(OutboundHistory::getBarcodeId, badBarcodeIds));
        }

        // 3b. 出库明细 + 出库单
        if (!badOutboundIds.isEmpty()) {
            deletedOutDetails = outboundDetailMapper.delete(
                new LambdaQueryWrapper<OutboundDetail>().in(OutboundDetail::getOutboundId, badOutboundIds));
            deletedOutOrders = outboundOrderMapper.deleteBatchIds(badOutboundIds);
        }

        // 3c. 封存记录
        if (!badBarcodeIds.isEmpty()) {
            deletedFreezes = inventoryFreezeMapper.delete(
                new LambdaQueryWrapper<InventoryFreeze>().in(InventoryFreeze::getBarcodeId, badBarcodeIds));
        }

        // 3d. 条码
        deletedBarcodes = barcodeMapper.deleteBatchIds(badBarcodeIds);

        // 3e. 入库明细
        if (!badInboundIds.isEmpty()) {
            deletedInDetails = inboundDetailMapper.delete(
                new LambdaQueryWrapper<InboundDetail>().in(InboundDetail::getInboundId, badInboundIds));
        }

        // 3f. 入库单
        if (!badInboundIds.isEmpty()) {
            // 再次确认：只删除那些已无关联明细的入库单
            for (Long inboundId : badInboundIds) {
                Long remaining = inboundDetailMapper.selectCount(
                    new LambdaQueryWrapper<InboundDetail>().eq(InboundDetail::getInboundId, inboundId));
                if (remaining == 0) {
                    inboundOrderMapper.deleteById(inboundId);
                    deletedInOrders++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "清理完成");
        result.put("bad8SegmentBarcodes", bad8Count);
        result.put("oldSplitBarcodes", oldSplitCount);
        result.put("deletedOutboundHistories", deletedHistories);
        result.put("deletedOutboundDetails", deletedOutDetails);
        result.put("deletedOutboundOrders", deletedOutOrders);
        result.put("deletedInventoryFreezes", deletedFreezes);
        result.put("deletedBarcodes", deletedBarcodes);
        result.put("deletedInboundDetails", deletedInDetails);
        result.put("deletedInboundOrders", deletedInOrders);

        log.info("[管理清理] 8段条码={} 旧拆分={} | 删除: 流水={} 出库明细={} 出库单={} 封存={} 条码={} 入库明细={} 入库单={}",
            bad8Count, oldSplitCount, deletedHistories, deletedOutDetails, deletedOutOrders,
            deletedFreezes, deletedBarcodes, deletedInDetails, deletedInOrders);

        return Result.success(result);
    }
}
