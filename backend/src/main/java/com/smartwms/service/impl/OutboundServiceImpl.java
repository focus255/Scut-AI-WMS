/**
 * 出库服务实现（支持按箱或按件出库，优先整箱、FIFO 拆零）。
 *
 * @author Focus
 * @date 2026-06-28
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.dto.ScanResponse;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.OutboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class OutboundServiceImpl implements OutboundService {

    /** 出库单号每日序号 */
    private static final AtomicInteger OUTBOUND_SEQ = new AtomicInteger(0);
    private static volatile String lastOutboundDate = "";

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundDetailMapper outboundDetailMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InventoryMapper inventoryMapper;
    private final BarcodeMapper barcodeMapper;
    private final ApplianceMapper applianceMapper;
    private final MaterialMapper materialMapper;

    public OutboundServiceImpl(OutboundOrderMapper outboundOrderMapper,
                               OutboundDetailMapper outboundDetailMapper,
                               OutboundHistoryMapper outboundHistoryMapper,
                               InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper,
                               ApplianceMapper applianceMapper,
                               MaterialMapper materialMapper) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundDetailMapper = outboundDetailMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.applianceMapper = applianceMapper;
        this.materialMapper = materialMapper;
    }

    /**
     * 根据物料号查询器具包装容量（使用物料默认供应商）。
     * 若未找到器具配置则抛出业务异常。
     */
    private int getOutPackCapacity(String materialCode) {
        // 先查物料获取默认供应商
        Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, materialCode)
        );
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料 " + materialCode + " 不存在");
        }
        String supplierCode = material.getSupplierCode();

        // 使用 selectList + limit 1 避免重复数据导致 TooManyResultsException
        List<Appliance> appliances = applianceMapper.selectList(
                new LambdaQueryWrapper<Appliance>()
                        .eq(Appliance::getMaterialCode, materialCode)
                        .eq(Appliance::getSupplierCode, supplierCode)
                        .last("LIMIT 1")
        );
        Appliance appliance = (appliances != null && !appliances.isEmpty()) ? appliances.get(0) : null;
        if (appliance == null || appliance.getPackCapacity() == null || appliance.getPackCapacity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "物料 " + materialCode + " 未配置器具包装容量，请先到器具管理页面配置。");
        }
        return appliance.getPackCapacity();
    }

    /**
     * 从数据库查询当天已有出库单号的最大序号，初始化计数器。
     * 避免应用重启后 OUTBOUND_SEQ 从 0 开始导致订单号重复。
     */
    private void initOutboundSeqFromDb(String datePart) {
        String likePrefix = "CK" + datePart;
        List<OutboundOrder> todayOrders = outboundOrderMapper.selectList(
            new LambdaQueryWrapper<OutboundOrder>()
                .likeRight(OutboundOrder::getOrderNo, likePrefix)
        );
        int maxSeq = 0;
        for (OutboundOrder o : todayOrders) {
            String no = o.getOrderNo();
            if (no != null && no.length() >= likePrefix.length()) {
                try {
                    int s = Integer.parseInt(no.substring(likePrefix.length()));
                    if (s > maxSeq) maxSeq = s;
                } catch (NumberFormatException ignored) { }
            }
        }
        OUTBOUND_SEQ.set(maxSeq);
    }

    /**
     * 创建出库单，按整箱 FIFO 选取入库二维码，不做拆零重封装。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrder create(OutboundOrderRequest request) {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!datePart.equals(lastOutboundDate)) {
            synchronized (OutboundServiceImpl.class) {
                if (!datePart.equals(lastOutboundDate)) {
                    initOutboundSeqFromDb(datePart);
                    lastOutboundDate = datePart;
                }
            }
        }
        String orderNo = "CK" + datePart + String.format("%05d", OUTBOUND_SEQ.incrementAndGet());
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未完成");
        outboundOrderMapper.insert(order);

        for (OutboundOrderRequest.OutboundDetailItem item : request.getDetails()) {
            createDetailAndPick(order, item);
        }
        return order;
    }

    /**
     * 分页查询出库单列表。
     */
    @Override
    public Page<OutboundOrder> page(int current, int size) {
        return page(current, size, null, null, null, null);
    }

    @Override
    public Page<OutboundOrder> page(int current, int size, String status, String orderNo,
                                     LocalDate startDate, LocalDate endDate) {
        Page<OutboundOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank())
            wrapper.eq(OutboundOrder::getStatus, status.trim());
        if (orderNo != null && !orderNo.isBlank())
            wrapper.like(OutboundOrder::getOrderNo, orderNo.trim());
        if (startDate != null)
            wrapper.ge(OutboundOrder::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null)
            wrapper.le(OutboundOrder::getCreatedAt, endDate.atTime(23, 59, 59));
        wrapper.orderByDesc(OutboundOrder::getCreatedAt);
        return outboundOrderMapper.selectPage(page, wrapper);
    }

    /**
     * 查询出库单详情及流水。
     */
    @Override
    public OutboundOrderVO getById(Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        }
        List<OutboundDetail> details = outboundDetailMapper.selectList(
                new LambdaQueryWrapper<OutboundDetail>().eq(OutboundDetail::getOutboundId, id)
        );
        // 一码到底：通过出库流水表关联的入库二维码
        List<OutboundHistory> outHistories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, id)
                        .orderByAsc(OutboundHistory::getId)
        );
        List<Long> barcodeIds = outHistories.stream()
                .map(OutboundHistory::getBarcodeId).filter(bid -> bid != null && bid > 0).distinct().toList();
        List<Barcode> barcodes = barcodeIds.isEmpty() ? List.of()
                : barcodeMapper.selectBatchIds(barcodeIds).stream()
                .sorted(Comparator.comparing(Barcode::getBarcode)).toList();
        List<OutboundHistoryVO> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, id)
                        .orderByAsc(OutboundHistory::getCreatedAt)
                        .orderByAsc(OutboundHistory::getId)
        ).stream().map(this::toHistoryVO).collect(Collectors.toList());
        return OutboundOrderVO.from(order, details, barcodes, histories);
    }

    /**
     * 确认出库并同步推进二维码生命周期。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long outboundId, ConfirmOutboundRequest request) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该出库单已完成核销");
        }
        if (request == null || request.getDetails() == null || request.getDetails().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库确认明细不能为空");
        }

        List<OutboundDetail> details = outboundDetailMapper.selectList(
                new LambdaQueryWrapper<OutboundDetail>().eq(OutboundDetail::getOutboundId, outboundId)
        );
        if (details.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库单明细不能为空");
        }

        Map<Long, OutboundDetail> detailMap = details.stream()
                .collect(Collectors.toMap(OutboundDetail::getId, detail -> detail));
        Map<String, Inventory> inventoryMap = new HashMap<>();
        Set<Long> processedDetailIds = new HashSet<>();
        Set<String> globalBarcodeSet = new HashSet<>();

        for (ConfirmOutboundRequest.ConfirmDetailItem item : request.getDetails()) {
            OutboundDetail detail = detailMap.get(item.getDetailId());
            if (detail == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库明细不存在或不属于当前出库单");
            }
            if (!processedDetailIds.add(detail.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "同一出库明细不能重复确认");
            }

            List<String> normalizedBarcodes = normalizeBarcodes(item.getBarcodes(), globalBarcodeSet);
            List<Barcode> selectedBarcodes = loadBarcodesInRequestOrder(normalizedBarcodes);
            validateSelectedBarcodes(detail, selectedBarcodes);

            int confirmedQty = selectedBarcodes.stream()
                    .mapToInt(bc -> bc.getRemainingQty() != null ? bc.getRemainingQty() : 0)
                    .sum();
            if (!item.getActualQty().equals(confirmedQty)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "出库明细 " + detail.getId() + " 的实际数量与二维码折算数量不一致");
            }

            int currentActualQty = detail.getActualQty() != null ? detail.getActualQty() : 0;
            int planQty = detail.getPlanQty() != null ? detail.getPlanQty() : 0;
            if (currentActualQty + confirmedQty > planQty) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "出库明细 " + detail.getId() + " 的累计实际出库数量不能超过计划数量");
            }

            Inventory inventory = inventoryMap.computeIfAbsent(detail.getMaterialCode(), this::loadInventory);
            int stockQty = inventory.getStockQty() != null ? inventory.getStockQty() : 0;
            if (stockQty < confirmedQty) {
                throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                        "库存不足：物料 " + detail.getMaterialCode() + " 需要 " + confirmedQty + "，当前仅剩 " + stockQty);
            }

            // 逐箱确认出库（流水已在拣货时创建，此处仅更新状态）
            for (Barcode outBarcode : selectedBarcodes) {
                outBarcode.setStatus("已出库");
                barcodeMapper.updateById(outBarcode);
            }

            detail.setActualQty(currentActualQty + confirmedQty);
            outboundDetailMapper.updateById(detail);
            inventory.setStockQty(stockQty - confirmedQty);
            inventoryMapper.updateById(inventory);
        }

        boolean allCompleted = details.stream().allMatch(detail -> {
            int planQty = detail.getPlanQty() != null ? detail.getPlanQty() : 0;
            int actualQty = detail.getActualQty() != null ? detail.getActualQty() : 0;
            return actualQty >= planQty;
        });
        boolean anyConfirmed = details.stream().anyMatch(detail ->
                (detail.getActualQty() != null ? detail.getActualQty() : 0) > 0);
        order.setStatus(allCompleted ? "已完成" : (anyConfirmed ? "部分完成" : "未完成"));
        outboundOrderMapper.updateById(order);
    }

    /**
     * 分页查询出库批次流水。
     */
    @Override
    public Page<OutboundHistoryVO> pageHistories(int current, int size, String orderNo, String materialCode) {
        Page<OutboundHistory> page = new Page<>(current, size);
        LambdaQueryWrapper<OutboundHistory> wrapper = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.like(OutboundHistory::getOutboundOrderNo, orderNo.trim());
        }
        if (materialCode != null && !materialCode.isBlank()) {
            wrapper.eq(OutboundHistory::getMaterialCode, materialCode.trim());
        }
        wrapper.orderByDesc(OutboundHistory::getCreatedAt)
                .orderByDesc(OutboundHistory::getId);
        Page<OutboundHistory> historyPage = outboundHistoryMapper.selectPage(page, wrapper);
        Page<OutboundHistoryVO> result = new Page<>(historyPage.getCurrent(), historyPage.getSize(), historyPage.getTotal());
        result.setRecords(historyPage.getRecords().stream().map(this::toHistoryVO).toList());
        return result;
    }

    private List<String> normalizeBarcodes(List<String> barcodes, Set<String> globalBarcodeSet) {
        List<String> normalized = new ArrayList<>();
        Set<String> localBarcodeSet = new HashSet<>();
        for (String barcode : barcodes) {
            String value = barcode != null ? barcode.trim() : null;
            if (value == null || value.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库二维码不能为空");
            }
            if (!localBarcodeSet.add(value) || !globalBarcodeSet.add(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库确认请求中存在重复二维码：" + value);
            }
            normalized.add(value);
        }
        return normalized;
    }

    private List<Barcode> loadBarcodesInRequestOrder(List<String> normalizedBarcodes) {
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>().in(Barcode::getBarcode, normalizedBarcodes)
        );
        if (barcodes.size() != normalizedBarcodes.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "存在未找到的出库二维码");
        }
        Map<String, Barcode> barcodeMap = barcodes.stream()
                .collect(Collectors.toMap(Barcode::getBarcode, barcode -> barcode, (a, b) -> a, LinkedHashMap::new));
        return normalizedBarcodes.stream().map(barcodeMap::get).toList();
    }

    private void validateSelectedBarcodes(OutboundDetail detail, List<Barcode> selectedBarcodes) {
        for (Barcode barcode : selectedBarcodes) {
            if (!detail.getMaterialCode().equals(barcode.getMaterialCode())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 " + barcode.getBarcode() + " 与出库明细物料不匹配");
            }
            if (!"inbound".equals(barcode.getType())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 " + barcode.getBarcode() + " 不是入库二维码，不可用于出库");
            }
            if (!"待出库".equals(barcode.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 " + barcode.getBarcode() + " 当前状态为 " + barcode.getStatus() + "，仅「待出库」状态可确认出库");
            }
        }
    }

    private Inventory loadInventory(String materialCode) {
        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, materialCode)
        );
        if (inventory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料 " + materialCode + " 尚未建立库存记录");
        }
        return inventory;
    }

    /**
     * 修改出库单：退回已拣库存，删除旧出库标签，重新执行整箱拣选。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutboundOrderRequest request) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        if ("已完成".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成的出库单不可修改");

        // 退回库存：将已拣的入库二维码恢复为在库
        rollbackPick(order.getId());
        // 删除旧明细和流水（一码到底：不删二维码，二维码由 rollbackPick 恢复）
        outboundDetailMapper.delete(new LambdaQueryWrapper<OutboundDetail>()
                .eq(OutboundDetail::getOutboundId, id));
        outboundHistoryMapper.delete(new LambdaQueryWrapper<OutboundHistory>()
                .eq(OutboundHistory::getOutboundId, id));

        // 重新创建明细并执行整箱拣选
        for (OutboundOrderRequest.OutboundDetailItem item : request.getDetails()) {
            createDetailAndPick(order, item);
        }

        // 重置状态
        order.setStatus("未完成");
        outboundOrderMapper.updateById(order);
    }

    /**
     * 删除出库单：退回库存，删除明细和流水。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");
        if (!"未完成".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅未完成状态的出库单可删除");

        rollbackPick(id);
        outboundDetailMapper.delete(new LambdaQueryWrapper<OutboundDetail>()
                .eq(OutboundDetail::getOutboundId, id));
        outboundHistoryMapper.delete(new LambdaQueryWrapper<OutboundHistory>()
                .eq(OutboundHistory::getOutboundId, id));
        outboundOrderMapper.deleteById(id);
    }

    /**
     * 退回已拣库存：通过出库流水找到被拣选的入库二维码，恢复为「在库」并退还扣除量。
     * 兼容整箱全取（待出库→在库，remainingQty 不变）、部分拆箱（在库，remainingQty 加回扣除量）
     * 和拆分二维码（删除拆分码，原箱恢复）。
     *
     * @author Focus
     * @date 2026-06-28
     */
    private void rollbackPick(Long outboundId) {
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, outboundId)
        );
        if (histories.isEmpty()) return;

        // 收集待删除的拆分二维码（含 _S 后缀的为拆箱重装生成）
        Set<Long> splitBarcodeIdsToDelete = new HashSet<>();

        for (OutboundHistory history : histories) {
            if (history.getBarcodeId() == null || history.getBarcodeId() == 0L) continue;
            Barcode inboundBc = barcodeMapper.selectById(history.getBarcodeId());
            if (inboundBc == null || !"inbound".equals(inboundBc.getType())) continue;

            String status = inboundBc.getStatus();
            int deductQty = history.getDeductQty() != null ? history.getDeductQty() : 0;

            // 拆分二维码（barcode 含 _S 后缀）：回退时直接删除
            if (inboundBc.getBarcode() != null && inboundBc.getBarcode().contains("_S")) {
                splitBarcodeIdsToDelete.add(inboundBc.getId());
                // 库存由该拆分对应的原箱流水恢复，此处不重复加回
                continue;
            }

            if ("待出库".equals(status)) {
                // 全取的箱：恢复为在库，remainingQty 保持原值（拣选时未扣减）
                inboundBc.setStatus("在库");
                barcodeMapper.updateById(inboundBc);
            } else if ("在库".equals(status)) {
                // 部分拆箱：退还扣除量到 remainingQty
                int currentRemaining = inboundBc.getRemainingQty() != null ? inboundBc.getRemainingQty() : 0;
                inboundBc.setRemainingQty(currentRemaining + deductQty);
                barcodeMapper.updateById(inboundBc);
            }

            // 恢复库存
            Inventory inv = loadInventory(inboundBc.getMaterialCode());
            inv.setStockQty((inv.getStockQty() != null ? inv.getStockQty() : 0) + deductQty);
            inventoryMapper.updateById(inv);
        }

        // 删除拆分二维码
        for (Long splitId : splitBarcodeIdsToDelete) {
            barcodeMapper.deleteById(splitId);
        }
    }

    private int getInboundPackCapacity(Barcode bc) {
        InboundDetail detail = inboundDetailMapper.selectOne(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, bc.getInboundId())
                        .eq(InboundDetail::getMaterialCode, bc.getMaterialCode())
        );
        return detail != null && detail.getPackCapacity() != null ? detail.getPackCapacity() : 0;
    }

    /**
     * 为单条明细执行智能拣选（核心方法，三阶段 FIFO：整箱优先→部分箱补充→拆整箱）。
     *
     * 流程：
     * 1. 查 Appliance 获取出库单箱容量
     * 2. planQty = 用户指定总件数 或 boxCount × packCapacity
     * 3. 阶段1 — 按 FIFO 选取整箱（remainingQty == packCapacity）
     * 4. 阶段2 — 整箱不够时，按 FIFO 从部分箱补充
     * 5. 阶段3 — 仍不够时，拆最早的一个整箱
     * 6. 全取的 barcode → "待出库"，部分取的 barcode → 保持"在库"仅扣减 remainingQty
     * 7. 扣减库存
     *
     * @author Focus
     * @date 2026-06-28
     */
    private void createDetailAndPick(OutboundOrder order, OutboundOrderRequest.OutboundDetailItem item) {
        String materialCode = item.getMaterialCode();
        int packCapacity = getOutPackCapacity(materialCode);

        // 确定计划总件数：planQty 优先，否则由 boxCount 推算
        int planQty;
        if (item.getPlanQty() != null && item.getPlanQty() > 0) {
            planQty = item.getPlanQty();
        } else {
            int boxCount = item.getBoxCount();
            if (boxCount <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "物料 " + materialCode + " 的出库箱数或计划件数必须大于 0");
            }
            planQty = boxCount * packCapacity;
        }

        OutboundDetail detail = new OutboundDetail();
        detail.setOutboundId(order.getId());
        detail.setOrderNo(order.getOrderNo());
        detail.setMaterialCode(materialCode);
        detail.setPackCapacity(packCapacity);
        detail.setPlanQty(planQty);
        detail.setActualQty(0);
        outboundDetailMapper.insert(detail);

        // 校验库存是否充足
        Inventory inventory = loadInventory(materialCode);
        int stockQty = inventory.getStockQty() != null ? inventory.getStockQty() : 0;
        if (stockQty < planQty) {
            int fullBoxes = stockQty / packCapacity;
            int remainder = stockQty % packCapacity;
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "库存不足：物料 " + materialCode + " 需要 " + planQty + " 件，当前仅剩 " + stockQty
                    + " 件（≈ " + fullBoxes + " 整箱 + " + remainder + " 件零头）");
        }

        // ============ 阶段1：按 FIFO 加载在库二维码（含整箱和部分箱） ============
        List<Barcode> allInStock = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getType, "inbound")
                        .eq(Barcode::getMaterialCode, materialCode)
                        .eq(Barcode::getStatus, "在库")
                        .gt(Barcode::getRemainingQty, 0)
        );

        // 分离整箱和部分箱
        List<Barcode> fullBoxes = new ArrayList<>();
        List<Barcode> partialBoxes = new ArrayList<>();
        for (Barcode bc : allInStock) {
            int expectedFull = getInboundPackCapacity(bc);
            int remaining = bc.getRemainingQty() != null ? bc.getRemainingQty() : 0;
            if (remaining >= expectedFull && expectedFull > 0) {
                fullBoxes.add(bc);
            } else if (remaining > 0) {
                partialBoxes.add(bc);
            }
        }

        // 均按 FIFO 排序（入库单创建时间 → 二维码创建时间 → ID）
        Map<Long, InboundOrder> orderCache = new HashMap<>();
        Comparator<Barcode> fifoComparator = Comparator
                .comparing((Barcode bc) -> {
                    InboundOrder io = orderCache.computeIfAbsent(bc.getInboundId(), inboundOrderMapper::selectById);
                    return io != null && io.getCreatedAt() != null ? io.getCreatedAt() : LocalDateTime.MAX;
                })
                .thenComparing(Barcode::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Barcode::getId);

        fullBoxes.sort(fifoComparator);
        partialBoxes.sort(fifoComparator);

        int remainingNeeded = planQty;
        // 记录每个被拣选 barcode 的扣除量
        Map<Long, Integer> pickMap = new LinkedHashMap<>(); // barcodeId → deductQty
        Set<Long> fullyConsumedIds = new HashSet<>();       // remainingQty 将归零的 barcode

        // ============ 阶段2：优先整箱选取（减少拆箱） ============
        for (Barcode bc : fullBoxes) {
            if (remainingNeeded <= 0) break;
            int boxQty = bc.getRemainingQty() != null ? bc.getRemainingQty() : 0;
            if (boxQty <= 0) continue;

            if (remainingNeeded >= boxQty) {
                // 整箱全取
                pickMap.put(bc.getId(), boxQty);
                fullyConsumedIds.add(bc.getId());
                remainingNeeded -= boxQty;
            } else {
                // 从整箱中拆出所需数量（阶段2不会进入此分支，留到阶段4统一处理）
                // 此处不处理，由阶段4统一拆箱
                break;
            }
        }

        // ============ 阶段3：部分箱补充（库存利用率最大化） ============
        if (remainingNeeded > 0) {
            for (Barcode bc : partialBoxes) {
                if (remainingNeeded <= 0) break;
                int boxQty = bc.getRemainingQty() != null ? bc.getRemainingQty() : 0;
                if (boxQty <= 0) continue;

                int take = Math.min(boxQty, remainingNeeded);
                pickMap.put(bc.getId(), take);
                if (take >= boxQty) {
                    fullyConsumedIds.add(bc.getId());
                }
                remainingNeeded -= take;
            }
        }

        // ============ 阶段4：拆整箱（最后手段，从 FIFO 最早未动的整箱中拆，生成拆分二维码） ============
        // 记录拆分信息：原 barcodeId → 拆分出的新 barcode
        Map<Long, Barcode> splitBarcodeMap = new LinkedHashMap<>();
        if (remainingNeeded > 0) {
            for (Barcode bc : fullBoxes) {
                if (remainingNeeded <= 0) break;
                if (pickMap.containsKey(bc.getId())) continue; // 已在阶段2全取

                int boxQty = bc.getRemainingQty() != null ? bc.getRemainingQty() : 0;
                if (boxQty <= remainingNeeded) continue; // 不够拆则跳过

                // 拆出所需数量，为拆出部分生成新的"待出库"二维码
                int splitQty = remainingNeeded;
                pickMap.put(bc.getId(), splitQty);
                // 原箱不归零：扣减后剩余继续在库
                // 为新拆分出的件数创建独立二维码
                Barcode splitBc = createSplitBarcode(bc, splitQty, packCapacity, order.getOrderNo());
                splitBarcodeMap.put(bc.getId(), splitBc);
                remainingNeeded = 0;
            }
        }

        // 理论上不会发生（阶段1已做总量校验），但保留防御性检查
        if (remainingNeeded > 0) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "库存不足：物料 " + materialCode + " 缺 " + remainingNeeded + " 件无法满足");
        }

        // ============ 执行拣选：更新 barcode 状态和 remainingQty ============
        for (Map.Entry<Long, Integer> entry : pickMap.entrySet()) {
            Long barcodeId = entry.getKey();
            int deductQty = entry.getValue();

            Barcode ib = barcodeMapper.selectById(barcodeId);
            if (ib == null) continue;

            int currentRemaining = ib.getRemainingQty() != null ? ib.getRemainingQty() : 0;
            Barcode splitBc = splitBarcodeMap.get(barcodeId); // 拆分出的新二维码（阶段4）

            if (splitBc != null) {
                // ===== 拆箱场景：原箱扣减后留在库，拆分件生成新二维码标记为待出库 =====
                ib.setRemainingQty(currentRemaining - deductQty);
                // 原箱保持"在库"状态不变
                barcodeMapper.updateById(ib);

                // 为拆分件创建出库流水（指向新生成的待出库二维码）
                createOutboundHistory(order, detail, materialCode, ib, splitBc, deductQty);

                // 同时为原箱创建流水（记录扣减，用于回退时恢复）
                createOutboundHistory(order, detail, materialCode, ib, ib, deductQty);
            } else if (fullyConsumedIds.contains(barcodeId)) {
                // ===== 整箱全取：标记为待出库，remainingQty 保留原值（确认时清零） =====
                ib.setStatus("待出库");
                barcodeMapper.updateById(ib);
                createOutboundHistory(order, detail, materialCode, ib, ib, deductQty);
            } else {
                // ===== 部分箱全取：remainingQty 归零，标记为待出库 =====
                ib.setRemainingQty(0);
                ib.setStatus("待出库");
                barcodeMapper.updateById(ib);
                createOutboundHistory(order, detail, materialCode, ib, ib, deductQty);
            }
        }

        // 扣减库存
        inventory.setStockQty(stockQty - planQty);
        inventoryMapper.updateById(inventory);
    }

    /**
     * 从整箱中拆出指定件数，生成新的"待出库"二维码（拆箱重装）。
     *
     * @param sourceBox   被拆分的原箱二维码
     * @param splitQty    拆分出的件数
     * @param packCapacity 标准箱容量
     * @param outboundOrderNo 出库单号
     * @return 新生成的拆分二维码（status=待出库）
     *
     * @author Focus
     * @date 2026-06-28
     */
    private Barcode createSplitBarcode(Barcode sourceBox, int splitQty, int packCapacity, String outboundOrderNo) {
        // 构造唯一的拆分二维码字符串：在原箱号后追加 _S 标识
        String splitBarcodeStr = sourceBox.getBarcode() + "_S" + System.currentTimeMillis() % 100000;
        Barcode splitBc = new Barcode();
        splitBc.setMaterialCode(sourceBox.getMaterialCode());
        splitBc.setSupplierCode(sourceBox.getSupplierCode());
        splitBc.setBarcode(splitBarcodeStr);
        splitBc.setStatus("待出库");
        splitBc.setInboundId(sourceBox.getInboundId());
        splitBc.setType("inbound");
        splitBc.setRemainingQty(splitQty);
        barcodeMapper.insert(splitBc);
        return splitBc;
    }

    /**
     * 创建出库流水记录。
     *
     * @param order     出库单
     * @param detail    出库明细
     * @param materialCode 物料号
     * @param sourceBox 源入库二维码（用于读取入库单信息）
     * @param targetBc  流水关联的二维码（整箱取时 = sourceBox，拆箱时为拆分二维码）
     * @param deductQty 本次扣减件数
     *
     * @author Focus
     * @date 2026-06-28
     */
    private void createOutboundHistory(OutboundOrder order, OutboundDetail detail,
                                        String materialCode, Barcode sourceBox,
                                        Barcode targetBc, int deductQty) {
        InboundDetail sourceDetail = inboundDetailMapper.selectOne(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, sourceBox.getInboundId())
                        .eq(InboundDetail::getMaterialCode, materialCode)
        );
        InboundOrder sourceOrder = inboundOrderMapper.selectById(sourceBox.getInboundId());
        OutboundHistory history = new OutboundHistory();
        history.setOutboundId(order.getId());
        history.setOutboundOrderNo(order.getOrderNo());
        history.setOutboundDetailId(detail.getId());
        history.setMaterialCode(materialCode);
        history.setInboundId(sourceBox.getInboundId());
        history.setInboundOrderNo(sourceOrder != null ? sourceOrder.getOrderNo() : "—");
        history.setInboundDetailId(sourceDetail != null ? sourceDetail.getId() : 0L);
        history.setBarcodeId(targetBc.getId());
        history.setBarcode(targetBc.getBarcode());
        history.setDeductQty(deductQty);
        outboundHistoryMapper.insert(history);
    }

    /**
     * 将出库流水实体转换为视图对象。
     */
    private OutboundHistoryVO toHistoryVO(OutboundHistory history) {
        OutboundHistoryVO vo = new OutboundHistoryVO();
        vo.setId(history.getId());
        vo.setOutboundOrderNo(history.getOutboundOrderNo());
        vo.setMaterialCode(history.getMaterialCode());
        vo.setInboundOrderNo(history.getInboundOrderNo());
        vo.setBarcodeId(history.getBarcodeId());
        vo.setBarcode(history.getBarcode());
        vo.setDeductQty(history.getDeductQty());
        vo.setCreatedAt(history.getCreatedAt());
        return vo;
    }

    /**
     * 扫码出库（一码到底）：直接扫描入库二维码（WMS|...）核销出库。
     * 仅接受状态为「待出库」的入库二维码。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScanResponse scanOutbound(String barcodeStr) {
        if (barcodeStr == null || !barcodeStr.startsWith("WMS|")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库请扫描入库二维码（WMS|... 格式），OUT标签已废弃");
        }

        // 查找入库二维码
        Barcode inboundBc = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getBarcode, barcodeStr)
                        .eq(Barcode::getType, "inbound")
        );
        if (inboundBc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "二维码不存在：" + barcodeStr);
        }
        if ("已出库".equals(inboundBc.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该二维码已出库，请勿重复扫码");
        }
        if ("FROZEN".equals(inboundBc.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该二维码已被封存，不可出库。请先解封");
        }
        if (!"待出库".equals(inboundBc.getStatus())) {
            if ("在库".equals(inboundBc.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "未创建出库单，禁止出库。请先在PC端出入库管理新建出库单");
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码当前状态为「" + inboundBc.getStatus() + "」，不可出库");
        }

        String materialCode = inboundBc.getMaterialCode();
        int boxQty = inboundBc.getRemainingQty() != null ? inboundBc.getRemainingQty() : 0;

        // 标记为已出库
        inboundBc.setStatus("已出库");
        inboundBc.setRemainingQty(0);
        barcodeMapper.updateById(inboundBc);

        // 通过出库流水找到关联的出库单
        OutboundHistory history = outboundHistoryMapper.selectOne(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getBarcodeId, inboundBc.getId())
                        .orderByDesc(OutboundHistory::getCreatedAt)
                        .last("limit 1")
        );
        Long outboundId = history != null ? history.getOutboundId() : null;
        if (outboundId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该二维码未关联出库单，无法扫码出库");
        }

        OutboundOrder order = outboundOrderMapper.selectById(outboundId);
        OutboundDetail detail = outboundDetailMapper.selectOne(
                new LambdaQueryWrapper<OutboundDetail>()
                        .eq(OutboundDetail::getOutboundId, outboundId)
                        .eq(OutboundDetail::getMaterialCode, materialCode)
        );
        if (detail != null) {
            int currentActual = detail.getActualQty() != null ? detail.getActualQty() : 0;
            detail.setActualQty(currentActual + boxQty);
            outboundDetailMapper.updateById(detail);
        }

        // 更新出库单状态
        List<OutboundDetail> allDetails = outboundDetailMapper.selectList(
                new LambdaQueryWrapper<OutboundDetail>().eq(OutboundDetail::getOutboundId, outboundId)
        );
        boolean allCompleted = allDetails.stream().allMatch(d -> {
            int plan = d.getPlanQty() != null ? d.getPlanQty() : 0;
            int act = d.getActualQty() != null ? d.getActualQty() : 0;
            return act >= plan;
        });
        boolean anyConfirmed = allDetails.stream().anyMatch(d -> (d.getActualQty() != null ? d.getActualQty() : 0) > 0);
        order.setStatus(allCompleted ? "已完成" : (anyConfirmed ? "部分完成" : "未完成"));
        outboundOrderMapper.updateById(order);

        return ScanResponse.outbound(order.getOrderNo(), materialCode, barcodeStr, boxQty);
    }
}
