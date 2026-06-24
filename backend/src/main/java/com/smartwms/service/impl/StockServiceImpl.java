/**
 * 库存报表服务实现 — 基于动态平衡的四级评级体系。
 * <p>
 * 评级规则优先级：库存为0（低储）→ 呆滞（90天无出库）→ 低储（跌破补货预警线）
 * → 高储（DOHF超标）→ 正常。
 * </p>
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.dto.StockReportVO;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.Material;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.service.StockService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {

    /** 呆滞判定阈值：超过此天数无出库记录即视为呆滞物料 */
    private static final int DEAD_STOCK_DAYS = 90;

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final BarcodeMapper barcodeMapper;

    public StockServiceImpl(InventoryMapper inventoryMapper,
                            MaterialMapper materialMapper,
                            OutboundHistoryMapper outboundHistoryMapper,
                            BarcodeMapper barcodeMapper) {
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.barcodeMapper = barcodeMapper;
    }

    @Override
    public List<StockReportVO> getStockReport(String materialCode, String alarmStatus) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();

        // 物料号模糊检索
        if (materialCode != null && !materialCode.isEmpty()) {
            wrapper.like(Inventory::getMaterialCode, materialCode);
        }

        List<Inventory> inventories = inventoryMapper.selectList(wrapper);
        List<StockReportVO> result = new ArrayList<>();

        for (Inventory inv : inventories) {
            StockReportVO vo = buildReportVO(inv);

            // 按水位状态过滤（兼容前端 LOW / HIGH / NORMAL / DEAD_STOCK 筛选）
            if (alarmStatus != null && !alarmStatus.isEmpty()) {
                if (!matchesFilter(vo.getRuleEvaluation(), alarmStatus)) {
                    continue;
                }
            }

            result.add(vo);
        }
        return result;
    }

    /**
     * 根据库存记录构建完整的报表视图对象，包含四级评级计算。
     */
    private StockReportVO buildReportVO(Inventory inv) {
        StockReportVO vo = new StockReportVO();
        vo.setMaterialCode(inv.getMaterialCode());
        vo.setStockQty(inv.getStockQty());
        vo.setMinStockDays(inv.getMinStockDays());
        vo.setMaxStockDays(inv.getMaxStockDays());
        vo.setSafetyStock(inv.getSafetyStock());
        vo.setLeadTimeDays(inv.getLeadTimeDays());
        vo.setUpdatedAt(inv.getUpdatedAt());

        // 查询物料名称
        Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, inv.getMaterialCode())
        );
        vo.setMaterialName(material != null ? material.getMaterialName() : inv.getMaterialCode());

        int qty = inv.getStockQty() != null ? inv.getStockQty() : 0;
        int minDays = inv.getMinStockDays() != null ? inv.getMinStockDays() : 3;
        int maxDays = inv.getMaxStockDays() != null ? inv.getMaxStockDays() : 15;
        int leadTimeDays = inv.getLeadTimeDays() != null ? inv.getLeadTimeDays() : 7;
        int safetyStock = inv.getSafetyStock() != null ? inv.getSafetyStock() : 0;

        // 计算日均消耗（近30天出库流水）
        double dailyConsume = computeDailyConsume(inv.getMaterialCode());
        vo.setDailyConsume(dailyConsume);

        // 查询最后出库日期与闲置天数
        LocalDateTime lastOutboundDate = getLastOutboundDate(inv.getMaterialCode());
        vo.setLastOutboundDate(lastOutboundDate);

        int idleDays = 0;
        if (lastOutboundDate != null) {
            idleDays = (int) ChronoUnit.DAYS.between(lastOutboundDate, LocalDateTime.now());
        } else {
            // 从未完成过，以最早入库二维码时间作为起始参考点
            LocalDateTime firstInbound = getFirstInboundDate(inv.getMaterialCode());
            if (firstInbound != null) {
                idleDays = (int) ChronoUnit.DAYS.between(firstInbound, LocalDateTime.now());
            }
        }
        vo.setIdleDays(idleDays);

        // 计算 DOHF（库存未来持有天数）
        if (dailyConsume > 0) {
            vo.setDohf((double) qty / dailyConsume);
        } else {
            // 无消耗时 DOHF 视为极大值（仅在非呆滞时用于高储判定）
            vo.setDohf(qty > 0 ? 9999.0 : 0.0);
        }

        // ==================== 四级评级逻辑 ====================
        // 优先级：库存为0 → 呆滞 → 低储 → 高储 → 正常
        vo.setRuleEvaluation(evaluateStockLevel(qty, dailyConsume, leadTimeDays, safetyStock,
                maxDays, idleDays));

        return vo;
    }

    /**
     * 按优先级执行四级库存评级。
     *
     * @param qty           当前库存量
     * @param dailyConsume  近30天日均消耗
     * @param leadTimeDays  补货提前期天数
     * @param safetyStock   安全库存量
     * @param maxStockDays  高储控制天数上限
     * @param idleDays      闲置天数（自最后一次出库至今）
     * @return 评级结果：LOW_STOCK / DEAD_STOCK / HIGH / NORMAL
     */
    private String evaluateStockLevel(int qty, double dailyConsume, int leadTimeDays,
                                      int safetyStock, int maxStockDays, int idleDays) {
        // 优先级1：库存为零 → 紧急低储
        if (qty <= 0) {
            return "LOW_STOCK";
        }

        // 优先级2：呆滞检测 — 超过90天无出库记录
        if (idleDays >= DEAD_STOCK_DAYS) {
            return "DEAD_STOCK";
        }

        // 优先级3：低储检测 — 库存低于补货预警线
        // 预警线 = (日均销量 × 补货提前期) + 安全库存
        double lowThreshold = (dailyConsume * leadTimeDays) + safetyStock;
        if (dailyConsume > 0 && qty < lowThreshold) {
            return "LOW_STOCK";
        }

        // 优先级4：高储检测 — DOHF 超过最高控制天数
        // DOHF = 当前库存 / 日均销量 > maxStockDays
        if (dailyConsume > 0) {
            double dohf = qty / dailyConsume;
            if (dohf > maxStockDays) {
                return "HIGH";
            }
        }

        // 优先级5：正常水位
        return "NORMAL";
    }

    /**
     * 根据近30天出库流水计算物料日均消耗量。
     * 若无出库记录则返回 0，由呆滞检测兜底。
     */
    private double computeDailyConsume(String materialCode) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getMaterialCode, materialCode)
                        .ge(OutboundHistory::getCreatedAt, thirtyDaysAgo)
        );
        if (histories.isEmpty()) {
            return 0.0;
        }
        int totalDeduct = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0)
                .sum();
        return Math.max(0.0, (double) totalDeduct / 30.0);
    }

    /**
     * 获取物料最后一次出库日期。
     *
     * @return 最后出库时间，若从未完成则返回 null
     */
    private LocalDateTime getLastOutboundDate(String materialCode) {
        OutboundHistory last = outboundHistoryMapper.selectOne(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getMaterialCode, materialCode)
                        .orderByDesc(OutboundHistory::getCreatedAt)
                        .last("LIMIT 1")
        );
        return last != null ? last.getCreatedAt() : null;
    }

    /**
     * 获取物料最早入库二维码日期（用于从未完成的物料计算闲置起点）。
     *
     * @return 最早入库时间，若二维码表也无记录则返回 null
     */
    private LocalDateTime getFirstInboundDate(String materialCode) {
        Barcode first = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getMaterialCode, materialCode)
                        .eq(Barcode::getType, "inbound")
                        .orderByAsc(Barcode::getCreatedAt)
                        .last("LIMIT 1")
        );
        return first != null ? first.getCreatedAt() : null;
    }

    /**
     * 检查评级结果是否匹配前端筛选条件。
     * 前端 "LOW" 映射到后端的 "LOW_STOCK"。
     */
    private boolean matchesFilter(String evaluation, String filter) {
        if (evaluation.equals(filter)) {
            return true;
        }
        // 兼容前端用 "LOW" 筛选低储
        return "LOW".equals(filter) && "LOW_STOCK".equals(evaluation);
    }
}
