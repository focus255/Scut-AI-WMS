/**
 * 出库服务实现（整箱出库，以箱为最小单位，不做拆零重封装）。
 *
 * @author Focus
 * @date 2026-06-23
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
import java.util.stream.Collectors;

@Service
public class OutboundServiceImpl implements OutboundService {

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

        Appliance appliance = applianceMapper.selectOne(
                new LambdaQueryWrapper<Appliance>()
                        .eq(Appliance::getMaterialCode, materialCode)
                        .eq(Appliance::getSupplierCode, supplierCode)
        );
        if (appliance == null || appliance.getPackCapacity() == null || appliance.getPackCapacity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "物料 " + materialCode + " 未配置器具包装容量，请先到器具管理页面配置。");
        }
        return appliance.getPackCapacity();
    }

    /**
     * 创建出库单，按整箱 FIFO 选取入库二维码，不做拆零重封装。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrder create(OutboundOrderRequest request) {
        String orderNo = "CK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未出库");
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
        order.setStatus(allCompleted ? "已完成" : (anyConfirmed ? "部分出库" : "未出库"));
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
        order.setStatus("未出库");
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
        if (!"未出库".equals(order.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅未出库状态的出库单可删除");

        rollbackPick(id);
        outboundDetailMapper.delete(new LambdaQueryWrapper<OutboundDetail>()
                .eq(OutboundDetail::getOutboundId, id));
        outboundHistoryMapper.delete(new LambdaQueryWrapper<OutboundHistory>()
                .eq(OutboundHistory::getOutboundId, id));
        outboundOrderMapper.deleteById(id);
    }

    /**
     * 退回已拣库存：通过出库流水找到被拣选的入库二维码，恢复为「在库」。
     */
    private void rollbackPick(Long outboundId) {
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, outboundId)
        );
        if (histories.isEmpty()) return;

        for (OutboundHistory history : histories) {
            if (history.getBarcodeId() == null || history.getBarcodeId() == 0L) continue;
            Barcode inboundBc = barcodeMapper.selectById(history.getBarcodeId());
            if (inboundBc != null && "inbound".equals(inboundBc.getType()) && "待出库".equals(inboundBc.getStatus())) {
                inboundBc.setStatus("在库");
                inboundBc.setRemainingQty(getInboundPackCapacity(inboundBc));
                barcodeMapper.updateById(inboundBc);
                // 恢复库存
                Inventory inv = loadInventory(inboundBc.getMaterialCode());
                inv.setStockQty((inv.getStockQty() != null ? inv.getStockQty() : 0) + (history.getDeductQty() != null ? history.getDeductQty() : 0));
                inventoryMapper.updateById(inv);
            }
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
     * 为单条明细执行整箱拣选（核心方法，不拆零）。
     *
     * 流程：
     * 1. 查 Appliance 获取出库单箱容量
     * 2. planQty = boxCount × packCapacity
     * 3. 按 FIFO 查找该物料所有在库整箱二维码
     * 4. 选取最早 boxCount 个整箱，标记为已出库
     * 5. 生成出库标签（每箱一个）
     * 6. 扣减库存
     */
    private void createDetailAndPick(OutboundOrder order, OutboundOrderRequest.OutboundDetailItem item) {
        String materialCode = item.getMaterialCode();
        int outPackCapacity = getOutPackCapacity(materialCode);
        int boxCount = item.getBoxCount();
        int planQty = boxCount * outPackCapacity;

        OutboundDetail detail = new OutboundDetail();
        detail.setOutboundId(order.getId());
        detail.setOrderNo(order.getOrderNo());
        detail.setMaterialCode(materialCode);
        detail.setPackCapacity(outPackCapacity);
        detail.setPlanQty(planQty);
        detail.setActualQty(0);
        outboundDetailMapper.insert(detail);

        if (boxCount <= 0) return;

        // 校验库存是否充足
        Inventory inventory = loadInventory(materialCode);
        int stockQty = inventory.getStockQty() != null ? inventory.getStockQty() : 0;
        if (stockQty < planQty) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "库存不足：物料 " + materialCode + " 需要 " + planQty + " 件（" + boxCount + " 箱 × " + outPackCapacity + "），当前仅剩 " + stockQty + " 件");
        }

        // 按 FIFO 查找该物料所有在库的入库二维码，只选取整箱（remainingQty = 原始 packCapacity）
        // 跳过封存（FROZEN）状态的二维码
        List<Barcode> inboundBarcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getType, "inbound")
                        .eq(Barcode::getMaterialCode, materialCode)
                        .eq(Barcode::getStatus, "在库")
                        .gt(Barcode::getRemainingQty, 0)
        );

        // 过滤整箱二维码
        List<Barcode> fullBoxBarcodes = new ArrayList<>();
        for (Barcode bc : inboundBarcodes) {
            int expectedFull = getInboundPackCapacity(bc);
            int remaining = bc.getRemainingQty() != null ? bc.getRemainingQty() : 0;
            if (remaining == expectedFull && expectedFull > 0) {
                fullBoxBarcodes.add(bc);
            }
        }

        // FIFO 排序
        Map<Long, InboundOrder> orderCache = new HashMap<>();
        fullBoxBarcodes.sort(Comparator
                .comparing((Barcode bc) -> {
                    InboundOrder io = orderCache.computeIfAbsent(bc.getInboundId(), inboundOrderMapper::selectById);
                    return io != null && io.getCreatedAt() != null ? io.getCreatedAt() : LocalDateTime.MAX;
                })
                .thenComparing(Barcode::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Barcode::getId));

        // 校验整箱库存是否足够
        if (fullBoxBarcodes.size() < boxCount) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "整箱库存不足：物料 " + materialCode + " 需要 " + boxCount + " 个整箱，当前仅有 " + fullBoxBarcodes.size() + " 个整箱在库（该物料不允许拆零出库）");
        }

        // 选取最早 boxCount 个整箱，标记为「待出库」（一码到底，不生成新标签）
        List<Barcode> selectedBoxes = fullBoxBarcodes.subList(0, boxCount);
        for (int i = 0; i < selectedBoxes.size(); i++) {
            Barcode ib = selectedBoxes.get(i);
            ib.setStatus("待出库");
            // 暂存出库单ID到二维码（复用 inboundId 字段在 type=inbound 时仍保留原入库单ID，此处用 supplierCode 存出库单标识）
            // 实际通过 OutboundHistory 表关联即可
            barcodeMapper.updateById(ib);

            // 记录出库流水，关联源入库二维码
            InboundDetail sourceDetail = inboundDetailMapper.selectOne(
                    new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, ib.getInboundId())
                            .eq(InboundDetail::getMaterialCode, materialCode)
            );
            InboundOrder sourceOrder = inboundOrderMapper.selectById(ib.getInboundId());
            OutboundHistory history = new OutboundHistory();
            history.setOutboundId(order.getId());
            history.setOutboundOrderNo(order.getOrderNo());
            history.setOutboundDetailId(detail.getId());
            history.setMaterialCode(materialCode);
            history.setInboundId(ib.getInboundId());
            history.setInboundOrderNo(sourceOrder != null ? sourceOrder.getOrderNo() : "—");
            history.setInboundDetailId(sourceDetail != null ? sourceDetail.getId() : 0L);
            history.setBarcodeId(ib.getId()); // 源入库二维码 ID
            history.setBarcode(ib.getBarcode());
            history.setDeductQty(outPackCapacity);
            outboundHistoryMapper.insert(history);
        }

        // 扣减库存
        inventory.setStockQty(stockQty - planQty);
        inventoryMapper.updateById(inventory);
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
        order.setStatus(allCompleted ? "已完成" : (anyConfirmed ? "部分出库" : "未出库"));
        outboundOrderMapper.updateById(order);

        return ScanResponse.outbound(order.getOrderNo(), materialCode, barcodeStr, boxQty);
    }
}
