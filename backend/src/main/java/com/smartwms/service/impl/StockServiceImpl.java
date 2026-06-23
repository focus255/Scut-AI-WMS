/**
 * 库存报表服务实现。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.dto.StockReportVO;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.Material;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.service.StockService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;

    public StockServiceImpl(InventoryMapper inventoryMapper,
                            MaterialMapper materialMapper,
                            OutboundHistoryMapper outboundHistoryMapper) {
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
    }

    @Override
    public List<StockReportVO> getStockReport(String materialCode, String alarmStatus) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();

        // 物料编码模糊检索
        if (materialCode != null && !materialCode.isEmpty()) {
            wrapper.like(Inventory::getMaterialCode, materialCode);
        }

        List<Inventory> inventories = inventoryMapper.selectList(wrapper);
        List<StockReportVO> result = new ArrayList<>();

        for (Inventory inv : inventories) {
            StockReportVO vo = new StockReportVO();
            vo.setMaterialCode(inv.getMaterialCode());
            vo.setStockQty(inv.getStockQty());
            vo.setMinStockDays(inv.getMinStockDays());
            vo.setMaxStockDays(inv.getMaxStockDays());
            vo.setUpdatedAt(inv.getUpdatedAt());

            // 查询物料名称
            Material material = materialMapper.selectOne(
                    new LambdaQueryWrapper<Material>()
                            .eq(Material::getMaterialCode, inv.getMaterialCode())
            );
            vo.setMaterialName(material != null ? material.getMaterialName() : inv.getMaterialCode());

            // 内置规则评级：根据高低储阈值判断
            int qty = inv.getStockQty() != null ? inv.getStockQty() : 0;
            int minDays = inv.getMinStockDays() != null ? inv.getMinStockDays() : 3;
            int maxDays = inv.getMaxStockDays() != null ? inv.getMaxStockDays() : 15;
            // 从近30天出库流水计算真实日均消耗，无数据时默认10件/天
            double dailyConsume = computeDailyConsume(inv.getMaterialCode());
            double lowThreshold = dailyConsume * minDays;
            double highThreshold = dailyConsume * maxDays;

            if (qty < lowThreshold) {
                vo.setRuleEvaluation("LOW_STOCK");
            } else if (qty > highThreshold) {
                vo.setRuleEvaluation("HIGH");
            } else {
                vo.setRuleEvaluation("NORMAL");
            }

            // 按水位状态过滤
            if (alarmStatus != null && !alarmStatus.isEmpty()) {
                if (!vo.getRuleEvaluation().equals(alarmStatus) &&
                    !(alarmStatus.equals("LOW") && vo.getRuleEvaluation().equals("LOW_STOCK"))) {
                    continue;
                }
            }

            result.add(vo);
        }
        return result;
    }

    /**
     * 根据近30天出库流水计算物料日均消耗量。
     * 若无出库记录则返回默认值 10 件/天。
     */
    private double computeDailyConsume(String materialCode) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getMaterialCode, materialCode)
                        .ge(OutboundHistory::getCreatedAt, thirtyDaysAgo)
        );
        if (histories.isEmpty()) return 10.0;
        int totalDeduct = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0)
                .sum();
        return Math.max(1.0, (double) totalDeduct / 30.0);
    }
}
