/**
 * 入库单服务实现。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.ConfirmInboundRequest;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.dto.InboundOrderVO;
import com.smartwms.dto.InventoryTraceVO;
import com.smartwms.dto.ScanInboundRequest;
import com.smartwms.dto.ScanInboundVO;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.InboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class InboundServiceImpl implements InboundService {

    /** 每日入库单序号计数器（并发安全） */
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(0);

    /** 上一次生成单号的日期，用于检测跨日重置 */
    private static volatile String lastDate = "";

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InventoryMapper inventoryMapper;
    private final BarcodeMapper barcodeMapper;
    private final MaterialMapper materialMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;

    public InboundServiceImpl(InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper,
                               MaterialMapper materialMapper,
                               OutboundHistoryMapper outboundHistoryMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.materialMapper = materialMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrder create(InboundOrderRequest request) {
        // 生成唯一入库单号 RK + 日期 + 序号（AtomicInteger 防并发重复）
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!datePart.equals(lastDate)) {
            synchronized (InboundServiceImpl.class) {
                if (!datePart.equals(lastDate)) {
                    ORDER_SEQ.set(0);
                    lastDate = datePart;
                }
            }
        }
        String orderNo = "RK" + datePart + String.format("%04d", ORDER_SEQ.incrementAndGet());

        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未入库");
        order.setSupplierCode(request.getSupplierCode());
        inboundOrderMapper.insert(order);

        // 创建明细并生成条码
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            // 按箱数生成条码，每箱时间错开以确保 FIFO 排序可区分
            int boxCount = (int) Math.ceil((double) item.getPlanQty() / item.getPackCapacity());
            for (int i = 0; i < boxCount; i++) {
                String barcodeStr = buildBarcode(item.getMaterialCode(), request.getSupplierCode(), item.getPlanQty(),
                        item.getPackCapacity(), item.getPlanQty(), i + 1, orderNo);
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(barcodeStr);
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcode.setType("inbound");
                barcode.setRemainingQty(item.getPackCapacity());
                barcodeMapper.insert(barcode);
                // 逐箱错开 1 秒，确保 FIFO 排序可区分
                Barcode updateTime = new Barcode();
                updateTime.setId(barcode.getId());
                updateTime.setCreatedAt(LocalDateTime.now().plusSeconds(i));
                barcodeMapper.updateById(updateTime);
            }
        }

        return order;
    }

    @Override
    public InboundOrderVO getById(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        List<InboundDetail> details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, id)
        );
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, id)
                        .orderByAsc(Barcode::getBarcode)
        );
        return InboundOrderVO.from(order, details, barcodes);
    }

    @Override
    public Page<InboundOrder> page(int current, int size) {
        Page<InboundOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InboundOrder::getCreatedAt);
        return inboundOrderMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long inboundId, ConfirmInboundRequest request) {
        InboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该入库单已完成核销");
        }

        // 查询该入库单的所有明细行
        var details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, inboundId)
        );

        // 若前端传入了明细行实际数量，则按 materialCode 匹配更新；否则默认按计划数全量入库
        Map<String, Integer> actualQtyMap = null;
        if (request != null && request.getDetails() != null && !request.getDetails().isEmpty()) {
            actualQtyMap = request.getDetails().stream()
                    .collect(Collectors.toMap(
                            ConfirmInboundRequest.ConfirmDetailItem::getMaterialCode,
                            ConfirmInboundRequest.ConfirmDetailItem::getActualQty,
                            (a, b) -> a
                    ));
        }

        for (InboundDetail detail : details) {
            // 确定实际入库数：优先取前端传入值，否则取计划数
            int actualQty = (actualQtyMap != null && actualQtyMap.containsKey(detail.getMaterialCode()))
                    ? actualQtyMap.get(detail.getMaterialCode())
                    : detail.getPlanQty();
            detail.setActualQty(actualQty);
            inboundDetailMapper.updateById(detail);

            // 按入库单 ID 精确匹配条码，避免误更新其他入库单的同物料条码
            var barcodes = barcodeMapper.selectList(
                    new LambdaQueryWrapper<Barcode>()
                            .eq(Barcode::getInboundId, inboundId)
                            .eq(Barcode::getMaterialCode, detail.getMaterialCode())
                            .eq(Barcode::getStatus, "待入库")
            );
            for (Barcode bc : barcodes) {
                bc.setStatus("在库");
                barcodeMapper.updateById(bc);
            }

            // 增加库存
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>()
                            .eq(Inventory::getMaterialCode, detail.getMaterialCode())
            );
            if (inv != null) {
                int oldQty = inv.getStockQty();
                inv.setStockQty(oldQty + actualQty);
                inventoryMapper.updateById(inv);
            }
        }

        // 更新入库单状态
        order.setStatus("已完成");
        inboundOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrder update(Long id, InboundOrderRequest request) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成入库单不可修改");
        }

        // 更新供应商
        order.setSupplierCode(request.getSupplierCode());
        inboundOrderMapper.updateById(order);

        // 删除旧明细（按入库单 ID 精确删除）
        inboundDetailMapper.delete(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, id)
        );

        // 删除旧条码（按入库单 ID 精确删除）
        barcodeMapper.delete(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, id)
        );

        // 重新创建明细和条码
        String orderNo = order.getOrderNo();
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            int boxCount = (int) Math.ceil((double) item.getPlanQty() / item.getPackCapacity());
            for (int i = 0; i < boxCount; i++) {
                String barcodeStr = buildBarcode(item.getMaterialCode(), request.getSupplierCode(), item.getPlanQty(),
                        item.getPackCapacity(), item.getPlanQty(), i + 1, orderNo);
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(barcodeStr);
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcode.setType("inbound");
                barcode.setRemainingQty(item.getPackCapacity());
                barcodeMapper.insert(barcode);
                // 逐箱错开 1 秒，确保 FIFO 排序可区分
                Barcode updateTime2 = new Barcode();
                updateTime2.setId(barcode.getId());
                updateTime2.setCreatedAt(LocalDateTime.now().plusSeconds(i));
                barcodeMapper.updateById(updateTime2);
            }
        }

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScanInboundVO scanReceive(ScanInboundRequest request) {
        String barcodeStr = request.getBarcode().trim();

        // 查找已有条码
        Barcode barcode = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getBarcode, barcodeStr)
        );

        // —— 条码不存在：解析创建新条码并关联入库单 ——
        if (barcode == null) {
            barcode = createBarcodeFromScan(barcodeStr);
        }

        // 校验条码状态
        if ("在库".equals(barcode.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "条码 " + barcodeStr + " 已入库，无需重复操作");
        }
        if ("已出库".equals(barcode.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "条码 " + barcodeStr + " 已出库，不可入库");
        }

        // 入库核销
        return doScanReceive(barcode, request.getActualQty());
    }

    @Override
    public InventoryTraceVO trace(String materialCode, String barcode, String orderNo) {
        boolean hasMaterial = materialCode != null && !materialCode.isEmpty();
        boolean hasBarcode = barcode != null && !barcode.isEmpty();
        boolean hasOrderNo = orderNo != null && !orderNo.isEmpty();
        if (!hasMaterial && !hasBarcode && !hasOrderNo) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供至少一个查询条件（物料编码/条码号/入库单号）");
        }

        // 如果指定了入库单号，先查入库单 ID，再通过 inboundId 查条码
        Long inboundId = null;
        if (hasOrderNo) {
            InboundOrder order = inboundOrderMapper.selectOne(
                    new LambdaQueryWrapper<InboundOrder>()
                            .like(InboundOrder::getOrderNo, "%" + orderNo + "%")
            );
            if (order != null) {
                inboundId = order.getId();
            }
        }

        // 按条件查询条码（仅入库类型，排除出库标签）
        LambdaQueryWrapper<Barcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Barcode::getType, "inbound");
        if (hasMaterial) {
            wrapper.eq(Barcode::getMaterialCode, materialCode);
        }
        if (hasBarcode) {
            wrapper.eq(Barcode::getBarcode, barcode);
        }
        if (inboundId != null) {
            wrapper.eq(Barcode::getInboundId, inboundId);
        } else if (hasOrderNo) {
            // 入库单号未匹配到任何订单，返回空列表
            return InventoryTraceVO.of(new ArrayList<>());
        }
        wrapper.orderByDesc(Barcode::getCreatedAt);

        List<Barcode> barcodes = barcodeMapper.selectList(wrapper);
        List<InventoryTraceVO.TraceItem> items = new ArrayList<>();
        for (Barcode bc : barcodes) {
            InboundDetail detail = null;
            if (bc.getInboundId() != null) {
                detail = inboundDetailMapper.selectOne(
                        new LambdaQueryWrapper<InboundDetail>()
                                .eq(InboundDetail::getInboundId, bc.getInboundId())
                                .eq(InboundDetail::getMaterialCode, bc.getMaterialCode())
                );
            }
            OutboundHistory outboundHistory = outboundHistoryMapper.selectOne(
                    new LambdaQueryWrapper<OutboundHistory>()
                            .eq(OutboundHistory::getBarcodeId, bc.getId())
                            .orderByDesc(OutboundHistory::getCreatedAt)
                            .last("limit 1")
            );
            items.add(InventoryTraceVO.TraceItem.from(bc, detail, outboundHistory));
        }

        return InventoryTraceVO.of(items);
    }

    // ==================== 扫码入库辅助方法 ====================

    /**
     * 从未知条码中解析入库数据并自动创建入库单和条码记录。
     *
     * 新格式（推荐）：WMS|物料|供应商|计划数|箱容量|实收数|箱号
     * 旧格式（兼容）：以 "-" 分隔的旧式条码，提取物料编码后使用默认值
     */
    private Barcode createBarcodeFromScan(String barcodeStr) {
        String materialCode;
        String supplierCode;
        int planQty;
        int packCapacity;
        int actualQtyFromBarcode;

        // 尝试解析新格式 WMS|...|...
        if (barcodeStr.startsWith("WMS|")) {
            String[] parts = barcodeStr.split("\\|");
            if (parts.length >= 6) {
                materialCode = parts[1];
                supplierCode = parts[2];
                planQty = parseIntSafe(parts[3], 1);
                packCapacity = parseIntSafe(parts[4], 1);
                actualQtyFromBarcode = parseIntSafe(parts[5], packCapacity);
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "条码格式错误（WMS|物料|供应商|计划数|箱容量|实收数|箱号），当前仅有 " + parts.length + " 个字段");
            }
        } else {
            // 兼容旧格式：从字符串中提取物料编码，使用默认值
            materialCode = extractMaterialCode(barcodeStr);
            if (materialCode == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "条码 " + barcodeStr + " 格式不正确，无法识别物料编码。请使用 WMS|物料|供应商|计划|箱容|实收|箱号 格式。");
            }
            supplierCode = null;
            planQty = 1;
            packCapacity = 1;
            actualQtyFromBarcode = 1;
        }

        // 校验物料是否存在
        Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, materialCode)
        );
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "物料 " + materialCode + " 不存在，请先在物料管理中创建该物料。");
        }

        // 供应商：优先条码中的值，其次物料默认供应商
        if (supplierCode == null || supplierCode.isEmpty()) {
            supplierCode = material.getSupplierCode();
        }

        // 查找可复用的未入库订单（actualQty < planQty 说明还有未收完的）
        InboundOrder order = null;
        List<InboundDetail> pendingDetails = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getMaterialCode, materialCode)
                        .apply("actual_qty < plan_qty")
                        .orderByAsc(InboundDetail::getCreatedAt)
        );
        if (!pendingDetails.isEmpty()) {
            InboundOrder candidate = inboundOrderMapper.selectById(pendingDetails.get(0).getInboundId());
            if (candidate != null && "未入库".equals(candidate.getStatus())) {
                order = candidate;
            }
        }

        // 无可用订单 → 自动创建
        if (order == null) {
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (!datePart.equals(lastDate)) {
                synchronized (InboundServiceImpl.class) {
                    if (!datePart.equals(lastDate)) {
                        ORDER_SEQ.set(0);
                        lastDate = datePart;
                    }
                }
            }
            String orderNo = "RK" + datePart + String.format("%04d", ORDER_SEQ.incrementAndGet());

            order = new InboundOrder();
            order.setOrderNo(orderNo);
            order.setStatus("未入库");
            order.setSupplierCode(supplierCode);
            inboundOrderMapper.insert(order);

            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(materialCode);
            detail.setPackCapacity(packCapacity);
            detail.setPlanQty(planQty);
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);
        }

        // 创建条码记录
        Barcode barcode = new Barcode();
        barcode.setBarcode(barcodeStr);
        barcode.setMaterialCode(materialCode);
        barcode.setSupplierCode(supplierCode);
        barcode.setInboundId(order.getId());
        barcode.setStatus("待入库");
        barcode.setType("inbound");
        barcode.setRemainingQty(packCapacity);
        barcodeMapper.insert(barcode);

        return barcode;
    }

    private String buildBarcode(String materialCode,
                                String supplierCode,
                                int planQty,
                                int packCapacity,
                                int actualQty,
                                int boxNo,
                                String orderNo) {
        return String.format("WMS|%s|%s|%d|%d|%d|%d|%s",
                materialCode,
                supplierCode,
                planQty,
                packCapacity,
                actualQty,
                boxNo,
                orderNo);
    }

    private int parseIntSafe(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return defaultVal; }
    }

    /** 从旧格式条码中提取物料编码（向后兼容） */
    private String extractMaterialCode(String barcodeStr) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(M_PART_\\d+|MATERIAL_\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(barcodeStr);
        if (m.find()) return m.group(0).toUpperCase();
        return null;
    }

    /**
     * 执行条码核销入库（提取自原 scanReceive 方法）。
     */
    private ScanInboundVO doScanReceive(Barcode barcode, Integer requestedQty) {
        // 获取关联的入库明细
        InboundDetail detail = null;
        if (barcode.getInboundId() != null) {
            detail = inboundDetailMapper.selectOne(
                    new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, barcode.getInboundId())
                            .eq(InboundDetail::getMaterialCode, barcode.getMaterialCode())
            );
        }

        // 确定实际入库数量
        int actualQty = requestedQty != null ? requestedQty
                : (detail != null && detail.getPackCapacity() != null ? detail.getPackCapacity() : 1);

        // 更新条码状态
        barcode.setStatus("在库");
        barcodeMapper.updateById(barcode);

        // 累加明细 actualQty
        if (detail != null) {
            int newActualQty = (detail.getActualQty() != null ? detail.getActualQty() : 0) + actualQty;
            detail.setActualQty(newActualQty);
            inboundDetailMapper.updateById(detail);

            // 检查入库单是否全部完成
            List<InboundDetail> allDetails = inboundDetailMapper.selectList(
                    new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, barcode.getInboundId())
            );
            boolean allDone = allDetails.stream()
                    .allMatch(d -> d.getActualQty() != null && d.getActualQty() >= d.getPlanQty());
            if (allDone) {
                InboundOrder order = inboundOrderMapper.selectById(barcode.getInboundId());
                if (order != null && !"已完成".equals(order.getStatus())) {
                    order.setStatus("已完成");
                    inboundOrderMapper.updateById(order);
                }
            }
        }

        // 增加库存
        Inventory inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getMaterialCode, barcode.getMaterialCode())
        );
        if (inv != null) {
            inv.setStockQty(inv.getStockQty() + actualQty);
            inventoryMapper.updateById(inv);
        }

        return ScanInboundVO.from(barcode, detail, true);
    }
}
