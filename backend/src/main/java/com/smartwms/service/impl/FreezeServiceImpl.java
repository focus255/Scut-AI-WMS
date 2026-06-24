/**
 * 封存解封服务实现。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.dto.FreezeRequest;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.InventoryFreeze;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InventoryFreezeMapper;
import com.smartwms.service.FreezeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FreezeServiceImpl implements FreezeService {

    private final InventoryFreezeMapper freezeMapper;
    private final BarcodeMapper barcodeMapper;

    public FreezeServiceImpl(InventoryFreezeMapper freezeMapper, BarcodeMapper barcodeMapper) {
        this.freezeMapper = freezeMapper;
        this.barcodeMapper = barcodeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seal(FreezeRequest request, String operator) {
        for (String barcodeStr : request.getBarcodes()) {
            Barcode bc = barcodeMapper.selectOne(
                    new LambdaQueryWrapper<Barcode>().eq(Barcode::getBarcode, barcodeStr.trim())
            );
            if (bc == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "二维码 " + barcodeStr + " 不存在");
            }
            if (!"在库".equals(bc.getStatus())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "二维码 " + barcodeStr + " 当前状态为 " + bc.getStatus() + "，仅「在库」状态可封存");
            }
            // 检查是否已封存
            Long exists = freezeMapper.selectCount(
                    new LambdaQueryWrapper<InventoryFreeze>()
                            .eq(InventoryFreeze::getBarcodeId, bc.getId())
                            .eq(InventoryFreeze::getStatus, "FROZEN")
            );
            if (exists > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码 " + barcodeStr + " 已封存，请勿重复操作");
            }

            // 更新二维码状态为封存
            bc.setStatus("FROZEN");
            barcodeMapper.updateById(bc);

            // 创建封存记录
            InventoryFreeze freeze = new InventoryFreeze();
            freeze.setBarcodeId(bc.getId());
            freeze.setMaterialCode(bc.getMaterialCode());
            freeze.setBarcode(barcodeStr.trim());
            freeze.setFreezeType(request.getFreezeType());
            freeze.setReason(request.getReason());
            freeze.setOperator(operator);
            freeze.setFreezeTime(LocalDateTime.now());
            freeze.setStatus("FROZEN");
            freezeMapper.insert(freeze);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unseal(String barcode, String operator) {
        Barcode bc = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>().eq(Barcode::getBarcode, barcode.trim())
        );
        if (bc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "二维码 " + barcode + " 不存在");
        }
        if (!"FROZEN".equals(bc.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "二维码 " + barcode + " 当前状态为 " + bc.getStatus() + "，非封存状态不可解封");
        }

        // 恢复二维码状态
        bc.setStatus("在库");
        barcodeMapper.updateById(bc);

        // 更新封存记录
        InventoryFreeze freeze = freezeMapper.selectOne(
                new LambdaQueryWrapper<InventoryFreeze>()
                        .eq(InventoryFreeze::getBarcodeId, bc.getId())
                        .eq(InventoryFreeze::getStatus, "FROZEN")
                        .orderByDesc(InventoryFreeze::getFreezeTime)
                        .last("limit 1")
        );
        if (freeze != null) {
            freeze.setStatus("UNFROZEN");
            freeze.setUnfreezeTime(LocalDateTime.now());
            freeze.setOperator(operator); // 记录解封操作人
            freezeMapper.updateById(freeze);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String freezeType, String reason) {
        InventoryFreeze freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new BusinessException(ErrorCode.NOT_FOUND, "封存记录不存在");
        if (!"FROZEN".equals(freeze.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅封存中的记录可编辑");
        freeze.setFreezeType(freezeType);
        freeze.setReason(reason);
        freezeMapper.updateById(freeze);
    }

    @Override
    public Page<InventoryFreeze> list(int page, int size, String materialCode, String status) {
        LambdaQueryWrapper<InventoryFreeze> wrapper = new LambdaQueryWrapper<>();
        if (materialCode != null && !materialCode.isBlank()) {
            wrapper.eq(InventoryFreeze::getMaterialCode, materialCode.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(InventoryFreeze::getStatus, status.trim());
        }
        wrapper.orderByDesc(InventoryFreeze::getFreezeTime);
        return freezeMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
