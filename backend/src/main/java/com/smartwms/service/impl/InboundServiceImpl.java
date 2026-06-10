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

    public InboundServiceImpl(InboundOrderMapper inboundOrderMapper,
                               InboundDetailMapper inboundDetailMapper,
                               InventoryMapper inventoryMapper,
                               BarcodeMapper barcodeMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inventoryMapper = inventoryMapper;
        this.barcodeMapper = barcodeMapper;
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

            // 按箱数生成条码，关联入库单 ID 用于精确追溯
            int boxCount = (int) Math.ceil((double) item.getPlanQty() / item.getPackCapacity());
            for (int i = 0; i < boxCount; i++) {
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(orderNo + "-" + item.getMaterialCode() + "-" + (i + 1));
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcodeMapper.insert(barcode);
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
        return InboundOrderVO.from(order, details);
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
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(orderNo + "-" + item.getMaterialCode() + "-" + (i + 1));
                barcode.setStatus("待入库");
                barcode.setInboundId(order.getId());
                barcodeMapper.insert(barcode);
            }
        }

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScanInboundVO scanReceive(ScanInboundRequest request) {
        String barcodeStr = request.getBarcode().trim();

        // 查找条码
        Barcode barcode = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getBarcode, barcodeStr)
        );
        if (barcode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "条码 " + barcodeStr + " 不存在，请核实条码是否正确");
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

        // 获取关联的入库明细
        InboundDetail detail = null;
        if (barcode.getInboundId() != null) {
            detail = inboundDetailMapper.selectOne(
                    new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, barcode.getInboundId())
                            .eq(InboundDetail::getMaterialCode, barcode.getMaterialCode())
            );
        }

        // 确定实际入库数量：优先取请求值，其次取明细箱容量，否则默认 1
        int actualQty = request.getActualQty() != null ? request.getActualQty()
                : (detail != null && detail.getPackCapacity() != null ? detail.getPackCapacity() : 1);

        // 更新条码状态为"在库"
        barcode.setStatus("在库");
        barcodeMapper.updateById(barcode);

        // 如果有关联明细，累加 actualQty
        if (detail != null) {
            int newActualQty = (detail.getActualQty() != null ? detail.getActualQty() : 0) + actualQty;
            detail.setActualQty(newActualQty);
            inboundDetailMapper.updateById(detail);

            // 检查该入库单所有明细是否已全部完成
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

        // 更新库存
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

    @Override
    public InventoryTraceVO trace(String materialCode, String barcode, String orderNo) {
        // 至少需要一个查询条件
        boolean hasMaterial = materialCode != null && !materialCode.isEmpty();
        boolean hasBarcode = barcode != null && !barcode.isEmpty();
        boolean hasOrderNo = orderNo != null && !orderNo.isEmpty();
        if (!hasMaterial && !hasBarcode && !hasOrderNo) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供至少一个查询条件（物料编码/条码号/入库单号）");
        }

        // 按条件查询条码
        LambdaQueryWrapper<Barcode> wrapper = new LambdaQueryWrapper<>();
        if (hasMaterial) {
            wrapper.eq(Barcode::getMaterialCode, materialCode);
        }
        if (hasBarcode) {
            wrapper.like(Barcode::getBarcode, "%" + barcode + "%");
        }
        if (hasOrderNo) {
            wrapper.like(Barcode::getBarcode, "%" + orderNo + "%");
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
            items.add(InventoryTraceVO.TraceItem.from(bc, detail));
        }

        return InventoryTraceVO.of(items);
    }
}
