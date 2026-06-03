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
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.service.StockService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;

    public StockServiceImpl(InventoryMapper inventoryMapper, MaterialMapper materialMapper) {
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
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
            // 日均消耗估算（桩：用安全线反推，后续接入真实出库数据）
            double dailyConsume = 10.0;
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
}
