/**
 * 入库单服务实现（支持按箱或按件入库，末箱允许零头）。
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
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ApplianceMapper applianceMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;

    public InboundServiceImpl(InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper,
                               MaterialMapper materialMapper,
                               ApplianceMapper applianceMapper,
                               OutboundHistoryMapper outboundHistoryMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.materialMapper = materialMapper;
        this.applianceMapper = applianceMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
    }

    /**
     * 根据物料号查询器具包装容量。
     * 优先按 物料+供应商 精确匹配，若未找到则按物料模糊匹配（支持多供应商物料）。
     */
    private int getPackCapacity(String materialCode, String supplierCode) {
        // 优先精确匹配（使用 selectList + LIMIT 1 避免重复数据导致 TooManyResultsException）
        List<Appliance> appliances = applianceMapper.selectList(
                new LambdaQueryWrapper<Appliance>()
                        .eq(Appliance::getMaterialCode, materialCode)
                        .eq(Appliance::getSupplierCode, supplierCode)
                        .last("LIMIT 1")
        );
        Appliance appliance = (appliances != null && !appliances.isEmpty()) ? appliances.get(0) : null;
        // 回退：按物料号匹配任意供应商
        if (appliance == null) {
            appliances = applianceMapper.selectList(
                    new LambdaQueryWrapper<Appliance>()
                            .eq(Appliance::getMaterialCode, materialCode)
                            .last("LIMIT 1")
            );
            appliance = (appliances != null && !appliances.isEmpty()) ? appliances.get(0) : null;
        }
        if (appliance == null || appliance.getPackCapacity() == null || appliance.getPackCapacity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "物料 " + materialCode + " 未配置器具包装容量，请先到器具管理页面配置。");
        }
        return appliance.getPackCapacity();
    }

    /** 物料+供应商 → 全局累加箱序号缓存（应用生命周期内不重置） */
    private static final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> BOX_SEQ_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 获取物料+供应商的下一个全局箱序号。
     * 首次访问时从数据库查询当前最大值 + 1，后续在内存中递增。
     */
    private int getNextBoxSeq(String materialCode, String supplierCode) {
        String key = materialCode + "|" + supplierCode;
        AtomicInteger counter = BOX_SEQ_CACHE.computeIfAbsent(key, k -> {
            // 从数据库查询该物料+供应商的当前最大箱序号
            int maxSeq = 0;
            List<Barcode> existing = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                    .eq(Barcode::getMaterialCode, materialCode)
                    .eq(Barcode::getSupplierCode, supplierCode)
                    .orderByDesc(Barcode::getId)
                    .last("LIMIT 1")
            );
            if (!existing.isEmpty()) {
                String barcode = existing.get(0).getBarcode();
                if (barcode != null) {
                    String[] parts = barcode.split("\\|");
                    if (parts.length >= 7) {
                        try { maxSeq = Integer.parseInt(parts[6].replaceAll("[^0-9]", "")); }
                        catch (NumberFormatException ignored) { }
                    }
                }
            }
            return new AtomicInteger(maxSeq);
        });
        return counter.incrementAndGet();
    }

    /**
     * 从数据库查询当天已有订单号的最大序号，初始化计数器。
     * 避免应用重启后 ORDER_SEQ 从 0 开始导致订单号重复。
     */
    private void initSeqFromDb(String datePart, String prefix, AtomicInteger seq) {
        List<InboundOrder> todayOrders = inboundOrderMapper.selectList(
            new LambdaQueryWrapper<InboundOrder>()
                .likeRight(InboundOrder::getOrderNo, prefix + datePart)
        );
        int maxSeq = 0;
        for (InboundOrder o : todayOrders) {
            String no = o.getOrderNo();
            // 标准格式 RK + 8位日期 + 4位序号 = 14字符，只取最后4位
            if (no != null && no.length() >= 14) {
                try {
                    int s = Integer.parseInt(no.substring(no.length() - 4));
                    if (s > maxSeq) maxSeq = s;
                } catch (NumberFormatException ignored) { }
            }
        }
        seq.set(maxSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrder create(InboundOrderRequest request) {
        // 生成唯一入库单号 RK + 日期 + 序号（从数据库初始化，防重启重复）
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!datePart.equals(lastDate)) {
            synchronized (InboundServiceImpl.class) {
                if (!datePart.equals(lastDate)) {
                    initSeqFromDb(datePart, "RK", ORDER_SEQ);
                    lastDate = datePart;
                }
            }
        }
        String orderNo = "RK" + datePart + String.format("%04d", ORDER_SEQ.incrementAndGet());

        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未完成");
        order.setSupplierCode(request.getSupplierCode());
        inboundOrderMapper.insert(order);

        // 收集所有物料+供应商用于订单摘要
        Set<String> supplierSet = new LinkedHashSet<>();
        supplierSet.add(request.getSupplierCode());

        // 创建明细并生成二维码（支持按件入库，末箱允许零头，多供应商）
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            // 解析本行实际的供应商：优先取明细自带值，其次查物料默认供应商
            String detailSupplier = item.getSupplierCode();
            if (detailSupplier == null || detailSupplier.isEmpty()) {
                Material mat = materialMapper.selectOne(
                    new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, item.getMaterialCode()));
                detailSupplier = (mat != null) ? mat.getSupplierCode() : request.getSupplierCode();
            }
            supplierSet.add(detailSupplier);

            int packCapacity = getPackCapacity(item.getMaterialCode(), detailSupplier);

            // 确定计划总件数和箱数：planQty 优先，否则由 boxCount 推算
            int planQty;
            int boxCount;
            if (item.getPlanQty() != null && item.getPlanQty() > 0) {
                planQty = item.getPlanQty();
                boxCount = (int) Math.ceil((double) planQty / packCapacity);
            } else {
                boxCount = item.getBoxCount() != null ? item.getBoxCount() : 0;
                if (boxCount <= 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "物料 " + item.getMaterialCode() + " 的入库箱数或计划件数必须大于 0");
                }
                planQty = boxCount * packCapacity;
            }

            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(packCapacity);
            detail.setPlanQty(planQty);
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            // 每箱生成一条二维码，前 N-1 箱为整箱，末箱可能为零头
            // 箱序号基于物料+供应商全局累加，不从1开始，避免不同订单重复
            for (int i = 0; i < boxCount; i++) {
                int globalBoxSeq = getNextBoxSeq(item.getMaterialCode(), detailSupplier);
                boolean isLastBox = (i == boxCount - 1);
                // 末箱件数 = planQty - 前 N-1 箱的整箱总量，若为零则回退到整箱容量（恰好整除时）
                int boxQty = isLastBox
                        ? planQty - (boxCount - 1) * packCapacity
                        : packCapacity;
                if (boxQty <= 0) boxQty = packCapacity; // 整除时末箱也是整箱

                String barcodeStr = buildBarcode(item.getMaterialCode(), detailSupplier,
                        planQty, packCapacity, boxQty, globalBoxSeq);
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(detailSupplier);
                barcode.setBarcode(barcodeStr);
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcode.setType("inbound");
                barcode.setRemainingQty(boxQty);
                barcodeMapper.insert(barcode);
                // 逐箱错开 1 秒，确保 FIFO 排序可区分
                Barcode updateTime = new Barcode();
                updateTime.setId(barcode.getId());
                updateTime.setCreatedAt(LocalDateTime.now().plusSeconds(i));
                barcodeMapper.updateById(updateTime);
            }
        }

        // 更新订单供应商为所有涉及供应商的串联
        order.setSupplierCode(String.join(",", supplierSet));
        inboundOrderMapper.updateById(order);

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<InboundOrder> batchCreate(List<InboundOrderRequest> requests) {
        List<InboundOrder> orders = new ArrayList<>();
        for (InboundOrderRequest req : requests) {
            orders.add(create(req));
        }
        return orders;
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

            // 按入库单 ID 精确匹配二维码，更新状态为在库
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

        Set<String> updateSupplierSet = new LinkedHashSet<>();
        updateSupplierSet.add(request.getSupplierCode());

        // 删除旧明细（按入库单 ID 精确删除）
        inboundDetailMapper.delete(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, id)
        );

        // 删除旧二维码（按入库单 ID 精确删除）
        barcodeMapper.delete(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getInboundId, id)
        );

        // 重新创建明细和二维码（支持按件入库，末箱允许零头，多供应商）
        String orderNo = order.getOrderNo();
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            // 解析本行实际的供应商
            String detailSupplier = item.getSupplierCode();
            if (detailSupplier == null || detailSupplier.isEmpty()) {
                Material mat = materialMapper.selectOne(
                    new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, item.getMaterialCode()));
                detailSupplier = (mat != null) ? mat.getSupplierCode() : request.getSupplierCode();
            }
            updateSupplierSet.add(detailSupplier);

            int packCapacity = getPackCapacity(item.getMaterialCode(), detailSupplier);

            // 确定计划总件数和箱数：planQty 优先，否则由 boxCount 推算
            int planQty;
            int boxCount;
            if (item.getPlanQty() != null && item.getPlanQty() > 0) {
                planQty = item.getPlanQty();
                boxCount = (int) Math.ceil((double) planQty / packCapacity);
            } else {
                boxCount = item.getBoxCount() != null ? item.getBoxCount() : 0;
                if (boxCount <= 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "物料 " + item.getMaterialCode() + " 的入库箱数或计划件数必须大于 0");
                }
                planQty = boxCount * packCapacity;
            }

            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(packCapacity);
            detail.setPlanQty(planQty);
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            for (int i = 0; i < boxCount; i++) {
                int globalBoxSeq = getNextBoxSeq(item.getMaterialCode(), detailSupplier);
                boolean isLastBox = (i == boxCount - 1);
                int boxQty = isLastBox
                        ? planQty - (boxCount - 1) * packCapacity
                        : packCapacity;
                if (boxQty <= 0) boxQty = packCapacity;

                String barcodeStr = buildBarcode(item.getMaterialCode(), detailSupplier,
                        planQty, packCapacity, boxQty, globalBoxSeq);
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(detailSupplier);
                barcode.setBarcode(barcodeStr);
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcode.setType("inbound");
                barcode.setRemainingQty(boxQty);
                barcodeMapper.insert(barcode);
                // 逐箱错开 1 秒，确保 FIFO 排序可区分
                Barcode updateTime2 = new Barcode();
                updateTime2.setId(barcode.getId());
                updateTime2.setCreatedAt(LocalDateTime.now().plusSeconds(i));
                barcodeMapper.updateById(updateTime2);
            }
        }

        order.setSupplierCode(String.join(",", updateSupplierSet));
        inboundOrderMapper.updateById(order);

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScanInboundVO scanReceive(ScanInboundRequest request) {
        String barcodeStr = request.getBarcode().trim();

        // 查找已有二维码
        Barcode barcode = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getBarcode, barcodeStr)
        );

        // —— 二维码不存在：解析创建新二维码并关联入库单 ——
        if (barcode == null) {
            barcode = createBarcodeFromScan(barcodeStr);
        }

        // 校验二维码状态：仅「待入库」或新建二维码可入库
        String status = barcode.getStatus();
        if ("在库".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码 " + barcodeStr + " 已入库，无需重复操作");
        }
        if ("已出库".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码 " + barcodeStr + " 已出库，不可入库");
        }
        if ("待出库".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码 " + barcodeStr + " 已被出库单拣选，不可入库。请先取消出库单");
        }
        if ("FROZEN".equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码 " + barcodeStr + " 已被封存，不可入库。请先解封");
        }

        // 入库核销
        return doScanReceive(barcode, request.getActualQty());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        if (!"未完成".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅未完成状态的入库单可删除");
        inboundDetailMapper.delete(new LambdaQueryWrapper<InboundDetail>().eq(InboundDetail::getInboundId, id));
        barcodeMapper.delete(new LambdaQueryWrapper<Barcode>().eq(Barcode::getInboundId, id));
        inboundOrderMapper.deleteById(id);
    }

    @Override
    public InventoryTraceVO trace(String materialCode, String barcode, String orderNo) {
        boolean hasMaterial = materialCode != null && !materialCode.isEmpty();
        boolean hasBarcode = barcode != null && !barcode.isEmpty();
        boolean hasOrderNo = orderNo != null && !orderNo.isEmpty();

        // 如果指定了入库单号，先查入库单 ID
        List<Long> inboundIds = null;
        if (hasOrderNo) {
            List<InboundOrder> orders = inboundOrderMapper.selectList(
                    new LambdaQueryWrapper<InboundOrder>()
                            .like(InboundOrder::getOrderNo, "%" + orderNo + "%")
                            .last("LIMIT 50")
            );
            if (orders.isEmpty()) {
                return InventoryTraceVO.of(new ArrayList<>());
            }
            // 支持多个订单匹配（模糊搜索可能命中多个）
            inboundIds = orders.stream().map(InboundOrder::getId).collect(Collectors.toList());
        }

        // 按条件查询二维码（仅入库类型），无任何条件时默认返回最近 200 条
        LambdaQueryWrapper<Barcode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Barcode::getType, "inbound");
        if (hasMaterial) wrapper.eq(Barcode::getMaterialCode, materialCode);
        if (hasBarcode) wrapper.eq(Barcode::getBarcode, barcode);
        if (inboundIds != null) wrapper.in(Barcode::getInboundId, inboundIds);
        wrapper.orderByDesc(Barcode::getCreatedAt);
        if (!hasMaterial && !hasBarcode && inboundIds == null) {
            wrapper.last("LIMIT 200");
        }

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
     * 从未知二维码中解析入库数据并自动创建入库单和二维码记录。
     *
     * 标准格式（7段）：WMS|物料号|供应商|计划总数|箱容量|箱内件数|箱序号
     * 兼容旧格式：段数不足时回退提取物料号
     */
    private Barcode createBarcodeFromScan(String barcodeStr) {
        String materialCode;
        String supplierCode;
        int planQty;
        int packCapacity;
        int actualQtyFromBarcode;

        if (barcodeStr.startsWith("WMS|")) {
            String[] parts = barcodeStr.split("\\|");
            if (parts.length >= 6) {
                materialCode = parts[1];
                supplierCode = parts[2];
                // 兼容 7/8 段：段3=计划总数(可忽略)，段4=箱容量，段5=箱内件数
                int seg3 = parseIntSafe(parts[3], 1);
                int seg4 = parseIntSafe(parts[4], 1);
                int seg5 = parseIntSafe(parts[5], seg4);
                packCapacity = seg4;
                actualQtyFromBarcode = seg5;
                planQty = seg3;  // 可能为 planQty（7段）或 stockQty（旧格式）
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码格式错误（需至少6段），当前仅有 " + parts.length + " 段");
            }
        } else {
            // 兼容旧格式：从字符串中提取物料号，使用默认值
            materialCode = extractMaterialCode(barcodeStr);
            if (materialCode == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 " + barcodeStr + " 格式不正确，无法识别物料号。请使用 WMS|物料|供应商|计划|箱容|实收|箱号 格式。");
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

        // 供应商：优先二维码中的值，其次物料默认供应商
        if (supplierCode == null || supplierCode.isEmpty()) {
            supplierCode = material.getSupplierCode();
        }

        // 优先从 Appliance 表获取 packCapacity，若二维码自带的值更可靠则以二维码为准
        if (packCapacity <= 1) {
            Appliance appliance = applianceMapper.selectOne(
                    new LambdaQueryWrapper<Appliance>()
                            .eq(Appliance::getMaterialCode, materialCode)
                            .eq(Appliance::getSupplierCode, supplierCode)
            );
            if (appliance != null && appliance.getPackCapacity() != null && appliance.getPackCapacity() > 0) {
                packCapacity = appliance.getPackCapacity();
            }
        }

        // 查找可复用的未完成订单（actualQty < planQty 说明还有未收完的）
        InboundOrder order = null;
        List<InboundDetail> pendingDetails = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getMaterialCode, materialCode)
                        .apply("actual_qty < plan_qty")
                        .orderByAsc(InboundDetail::getCreatedAt)
        );
        if (!pendingDetails.isEmpty()) {
            InboundOrder candidate = inboundOrderMapper.selectById(pendingDetails.get(0).getInboundId());
            if (candidate != null && "未完成".equals(candidate.getStatus())) {
                order = candidate;
            }
        }

        // 无可用订单 → 自动创建
        if (order == null) {
            String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            if (!datePart.equals(lastDate)) {
                synchronized (InboundServiceImpl.class) {
                    if (!datePart.equals(lastDate)) {
                        initSeqFromDb(datePart, "RK", ORDER_SEQ);
                        lastDate = datePart;
                    }
                }
            }
            String orderNo = "RK" + datePart + String.format("%04d", ORDER_SEQ.incrementAndGet());

            order = new InboundOrder();
            order.setOrderNo(orderNo);
            order.setStatus("未完成");
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

        // 创建二维码记录（整箱 remainingQty = packCapacity）
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

    /**
     * 构建看板号——箱子身份证，全生命周期唯一，仅含箱子本身信息。
     *
     * 格式（7段）：WMS|物料号|供应商|计划总数|箱容量|箱内件数|箱序号
     * 示例：WMS|M_PART_001|SUP_VWG_09|200|20|20|3
     *   → 第3箱，计划200件，箱容量20，本箱实收20件
     *
     * @author Focus
     * @date 2026-06-28
     */
    private String buildBarcode(String materialCode,
                                String supplierCode,
                                int planQty,
                                int packCapacity,
                                int actualQty,
                                int boxNo) {
        return String.format("WMS|%s|%s|%d|%d|%d|%d",
                materialCode,
                supplierCode,
                planQty,
                packCapacity,
                actualQty,
                boxNo);
    }

    private int parseIntSafe(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return defaultVal; }
    }

    /** 从旧格式二维码中提取物料号（向后兼容） */
    private String extractMaterialCode(String barcodeStr) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(M_PART_\\d+|MATERIAL_\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(barcodeStr);
        if (m.find()) return m.group(0).toUpperCase();
        return null;
    }

    /**
     * 执行二维码核销入库（提取自原 scanReceive 方法）。
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

        // 确定实际入库数量（默认为整箱容量）
        int actualQty = requestedQty != null ? requestedQty
                : (detail != null && detail.getPackCapacity() != null ? detail.getPackCapacity() : 1);

        // 更新二维码状态
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
