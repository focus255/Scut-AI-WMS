package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.ConfirmOutboundRequest;
import com.smartwms.dto.OutboundHistoryVO;
import com.smartwms.dto.OutboundOrderRequest;
import com.smartwms.dto.OutboundOrderVO;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.OutboundDetail;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.OutboundDetailMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.mapper.OutboundOrderMapper;
import com.smartwms.service.OutboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出库服务实现。
 */
@Service
public class OutboundServiceImpl implements OutboundService {

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundDetailMapper outboundDetailMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InventoryMapper inventoryMapper;
    private final BarcodeMapper barcodeMapper;

    public OutboundServiceImpl(OutboundOrderMapper outboundOrderMapper,
                               OutboundDetailMapper outboundDetailMapper,
                               OutboundHistoryMapper outboundHistoryMapper,
                               InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundDetailMapper = outboundDetailMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
    }

    /**
     * 创建出库单。
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
            OutboundDetail detail = new OutboundDetail();
            detail.setOutboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            outboundDetailMapper.insert(detail);
        }
        return order;
    }

    /**
     * 分页查询出库单列表。
     */
    @Override
    public Page<OutboundOrder> page(int current, int size) {
        Page<OutboundOrder> page = new Page<>(current, size);
        return outboundOrderMapper.selectPage(page,
                new LambdaQueryWrapper<OutboundOrder>().orderByDesc(OutboundOrder::getCreatedAt));
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
        List<OutboundHistoryVO> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getOutboundId, id)
                        .orderByAsc(OutboundHistory::getCreatedAt)
                        .orderByAsc(OutboundHistory::getId)
        ).stream().map(this::toHistoryVO).collect(Collectors.toList());
        return OutboundOrderVO.from(order, details, histories);
    }

    /**
     * 确认出库并同步推进条码生命周期。
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

        Map<Long, OutboundDetail> detailMap = details.stream().collect(Collectors.toMap(OutboundDetail::getId, detail -> detail));
        Map<String, Inventory> inventoryMap = new HashMap<>();
        Map<Long, InboundOrder> inboundOrderCache = new HashMap<>();
        Set<Long> processedDetailIds = new HashSet<>();
        Set<String> globalBarcodeSet = new HashSet<>();
        Set<Long> reservedBarcodeIds = new HashSet<>();

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
            validateFifo(detail, selectedBarcodes, reservedBarcodeIds, inboundOrderCache);

            int confirmedQty = calculateConfirmedQty(selectedBarcodes);
            if (!item.getActualQty().equals(confirmedQty)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "出库明细 " + detail.getId() + " 的实际数量与条码折算数量不一致");
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

            for (Barcode barcode : selectedBarcodes) {
                InboundDetail inboundDetail = loadInboundDetailForBarcode(barcode, detail.getMaterialCode());
                InboundOrder inboundOrder = inboundOrderCache.computeIfAbsent(barcode.getInboundId(), inboundOrderMapper::selectById);
                if (inboundOrder == null || !"已完成".equals(inboundOrder.getStatus())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "条码 " + barcode.getBarcode() + " 对应的入库批次尚未完成，不能出库");
                }

                int barcodeQty = calculateBarcodeQty(barcode, inboundDetail);
                OutboundHistory history = new OutboundHistory();
                history.setOutboundId(order.getId());
                history.setOutboundOrderNo(order.getOrderNo());
                history.setOutboundDetailId(detail.getId());
                history.setMaterialCode(detail.getMaterialCode());
                history.setInboundId(inboundOrder.getId());
                history.setInboundOrderNo(inboundOrder.getOrderNo());
                history.setInboundDetailId(inboundDetail.getId());
                history.setBarcodeId(barcode.getId());
                history.setBarcode(barcode.getBarcode());
                history.setDeductQty(barcodeQty);
                outboundHistoryMapper.insert(history);

                barcode.setStatus("已出库");
                barcodeMapper.updateById(barcode);
                reservedBarcodeIds.add(barcode.getId());
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
        boolean anyConfirmed = details.stream().anyMatch(detail -> (detail.getActualQty() != null ? detail.getActualQty() : 0) > 0);
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
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库条码不能为空");
            }
            if (!localBarcodeSet.add(value) || !globalBarcodeSet.add(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "出库确认请求中存在重复条码：" + value);
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "存在未找到的出库条码");
        }
        Map<String, Barcode> barcodeMap = barcodes.stream().collect(Collectors.toMap(Barcode::getBarcode, barcode -> barcode, (a, b) -> a, LinkedHashMap::new));
        return normalizedBarcodes.stream().map(barcodeMap::get).toList();
    }

    private void validateSelectedBarcodes(OutboundDetail detail, List<Barcode> selectedBarcodes) {
        for (Barcode barcode : selectedBarcodes) {
            if (!detail.getMaterialCode().equals(barcode.getMaterialCode())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "条码 " + barcode.getBarcode() + " 与出库明细物料不匹配");
            }
            if (!"在库".equals(barcode.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "条码 " + barcode.getBarcode() + " 当前状态为 " + barcode.getStatus() + "，不可出库");
            }
            if (barcode.getInboundId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "条码 " + barcode.getBarcode() + " 缺少来源入库信息，无法出库");
            }
        }
    }

    private void validateFifo(OutboundDetail detail,
                              List<Barcode> selectedBarcodes,
                              Set<Long> reservedBarcodeIds,
                              Map<Long, InboundOrder> inboundOrderCache) {
        List<Barcode> availableBarcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getMaterialCode, detail.getMaterialCode())
                        .eq(Barcode::getStatus, "在库")
        );
        availableBarcodes = availableBarcodes.stream()
                .filter(barcode -> !reservedBarcodeIds.contains(barcode.getId()))
                .filter(barcode -> {
                    InboundOrder inboundOrder = inboundOrderCache.computeIfAbsent(barcode.getInboundId(), inboundOrderMapper::selectById);
                    return inboundOrder != null && "已完成".equals(inboundOrder.getStatus());
                })
                .sorted(Comparator
                        .comparing((Barcode barcode) -> {
                            InboundOrder inboundOrder = inboundOrderCache.computeIfAbsent(barcode.getInboundId(), inboundOrderMapper::selectById);
                            return inboundOrder != null ? inboundOrder.getCreatedAt() : LocalDateTime.MAX;
                        })
                        .thenComparing(Barcode::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Barcode::getId))
                .toList();

        if (availableBarcodes.size() < selectedBarcodes.size()) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT,
                    "物料 " + detail.getMaterialCode() + " 的在库条码数量不足");
        }

        Set<Long> expectedIds = availableBarcodes.stream()
                .limit(selectedBarcodes.size())
                .map(Barcode::getId)
                .collect(Collectors.toSet());
        Set<Long> actualIds = selectedBarcodes.stream().map(Barcode::getId).collect(Collectors.toSet());
        if (!expectedIds.equals(actualIds)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "物料 " + detail.getMaterialCode() + " 未按先进先出规则选择条码");
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

    private InboundDetail loadInboundDetailForBarcode(Barcode barcode, String materialCode) {
        InboundDetail inboundDetail = inboundDetailMapper.selectOne(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, barcode.getInboundId())
                        .eq(InboundDetail::getMaterialCode, materialCode)
        );
        if (inboundDetail == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "条码 " + barcode.getBarcode() + " 未找到对应入库明细");
        }
        return inboundDetail;
    }

    private int calculateConfirmedQty(List<Barcode> selectedBarcodes) {
        return selectedBarcodes.stream()
                .mapToInt(barcode -> calculateBarcodeQty(barcode, loadInboundDetailForBarcode(barcode, barcode.getMaterialCode())))
                .sum();
    }

    private int calculateBarcodeQty(Barcode barcode, InboundDetail inboundDetail) {
        int packCapacity = inboundDetail.getPackCapacity() != null ? inboundDetail.getPackCapacity() : 1;
        int actualQty = inboundDetail.getActualQty() != null ? inboundDetail.getActualQty() : 0;
        int planQty = inboundDetail.getPlanQty() != null ? inboundDetail.getPlanQty() : actualQty;
        int boxCount = Math.max(1, (int) Math.ceil((double) Math.max(planQty, 1) / Math.max(packCapacity, 1)));
        int boxNo = parseBarcodeBoxNo(barcode.getBarcode());
        if (boxNo <= 0 || boxNo > boxCount) {
            return packCapacity;
        }
        if (boxNo < boxCount) {
            return packCapacity;
        }
        int lastQty = actualQty - packCapacity * (boxCount - 1);
        return lastQty > 0 ? lastQty : packCapacity;
    }

    private int parseBarcodeBoxNo(String barcode) {
        if (barcode == null || !barcode.startsWith("WMS|")) {
            return -1;
        }
        String[] parts = barcode.split("\\|");
        if (parts.length < 7) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[6]);
        } catch (NumberFormatException ex) {
            return -1;
        }
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
}
