/**
 * 入库单服务实现（桩实现，后续完善）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.InboundOrderRequest;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import com.smartwms.service.InboundService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class InboundServiceImpl implements InboundService {

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
        // 生成唯一入库单号 RK + 日期 + 序号
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo = "RK" + datePart + String.format("%03d", System.currentTimeMillis() % 1000);

        InboundOrder order = new InboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus("未入库");
        order.setSupplierCode(request.getSupplierCode());
        inboundOrderMapper.insert(order);

        // 创建明细
        for (InboundOrderRequest.InboundDetailItem item : request.getDetails()) {
            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(item.getMaterialCode());
            detail.setPackCapacity(item.getPackCapacity());
            detail.setPlanQty(item.getPlanQty());
            detail.setActualQty(0);
            inboundDetailMapper.insert(detail);

            // 自动生成条码
            int boxCount = (int) Math.ceil((double) item.getPlanQty() / item.getPackCapacity());
            for (int i = 0; i < boxCount; i++) {
                Barcode barcode = new Barcode();
                barcode.setMaterialCode(item.getMaterialCode());
                barcode.setSupplierCode(request.getSupplierCode());
                barcode.setBarcode(orderNo + "-" + item.getMaterialCode() + "-" + (i + 1));
                barcode.setStatus("待入库");
                barcodeMapper.insert(barcode);
            }
        }

        return order;
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
    public void confirm(Long inboundId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");
        }
        if ("已完成".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该入库单已完成核销");
        }

        // 更新所有明细的实际入库数为计划数（桩实现：默认全部到货）
        var details = inboundDetailMapper.selectList(
                new LambdaQueryWrapper<InboundDetail>()
                        .eq(InboundDetail::getInboundId, inboundId)
        );
        for (InboundDetail detail : details) {
            detail.setActualQty(detail.getPlanQty());
            inboundDetailMapper.updateById(detail);

            // 更新条码状态为 "在库"
            var barcodes = barcodeMapper.selectList(
                    new LambdaQueryWrapper<Barcode>()
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
                inv.setStockQty(oldQty + detail.getActualQty());
                inventoryMapper.updateById(inv);
            }
        }

        // 更新入库单状态
        order.setStatus("已完成");
        inboundOrderMapper.updateById(order);
    }
}
