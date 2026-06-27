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
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.Material;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.service.StockService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StockServiceImpl implements StockService {

    /** 呆滞判定阈值：超过此天数无出库记录即视为呆滞物料 */
    private static final int DEAD_STOCK_DAYS = 90;

    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final BarcodeMapper barcodeMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;

    public StockServiceImpl(InventoryMapper inventoryMapper,
                            MaterialMapper materialMapper,
                            OutboundHistoryMapper outboundHistoryMapper,
                            BarcodeMapper barcodeMapper,
                            InboundOrderMapper inboundOrderMapper,
                            InboundDetailMapper inboundDetailMapper) {
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
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
            vo.setDohf(qty > 0 ? 9999.0 : 0.0);
        }

        // 算法推荐值：基于历史数据计算安全库存和提前期
        int computedLeadTime = computeSuggestedLeadTime(inv.getMaterialCode(), leadTimeDays);
        int computedSafetyStock = computeSuggestedSafetyStock(inv.getMaterialCode(), dailyConsume, computedLeadTime);
        vo.setSuggestedLeadTimeDays(computedLeadTime);
        vo.setSuggestedSafetyStock(computedSafetyStock);

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
        return "LOW".equals(filter) && "LOW_STOCK".equals(evaluation);
    }

    /**
     * 基于历史入库间隔计算建议提前期。
     * 统计最近 5 次确认入库的平均间隔天数，若数据不足则保留手动配置值。
     *
     * @return 建议的提前期天数
     */
    private int computeSuggestedLeadTime(String materialCode, int manualDays) {
        List<InboundOrder> confirmedOrders = inboundOrderMapper.selectList(
            new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getStatus, "已完成")
                .orderByDesc(InboundOrder::getCreatedAt)
                .last("LIMIT 10")
        );
        // 筛选包含该物料的订单并按时间排序
        List<LocalDateTime> inboundTimes = new ArrayList<>();
        for (InboundOrder o : confirmedOrders) {
            Long count = inboundDetailMapper.selectCount(
                new LambdaQueryWrapper<InboundDetail>()
                    .eq(InboundDetail::getInboundId, o.getId())
                    .eq(InboundDetail::getMaterialCode, materialCode));
            if (count > 0 && o.getCreatedAt() != null) {
                inboundTimes.add(o.getCreatedAt());
            }
        }
        if (inboundTimes.size() < 2) return manualDays;
        // 按时间升序排列
        inboundTimes.sort(LocalDateTime::compareTo);
        // 计算平均补货间隔
        long totalDays = 0;
        for (int i = 1; i < inboundTimes.size(); i++) {
            totalDays += ChronoUnit.DAYS.between(inboundTimes.get(i - 1), inboundTimes.get(i));
        }
        int avgInterval = (int) (totalDays / (inboundTimes.size() - 1));
        return Math.max(1, avgInterval);
    }

    /**
     * 基于历史需求波动计算建议安全库存。
     * 使用经典公式：Z × σ_daily × √leadTimeDays
     * Z=1.65（95%服务水平），σ_daily 为近 30 天日出库量的标准差。
     *
     * @param dailyConsume   近 30 天日均消耗
     * @param leadTimeDays   补货提前期天数
     * @return 建议的安全库存量（向上取整）
     */
    private int computeSuggestedSafetyStock(String materialCode, double dailyConsume, int leadTimeDays) {
        if (dailyConsume <= 0) return 0;
        // 查询近 30 天每日出库量
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
            new LambdaQueryWrapper<OutboundHistory>()
                .eq(OutboundHistory::getMaterialCode, materialCode)
                .ge(OutboundHistory::getCreatedAt, thirtyDaysAgo)
        );
        if (histories.size() < 5) {
            // 数据不足时用日均 × 3 天的经验值兜底
            return (int) Math.ceil(dailyConsume * 3);
        }
        // 按日期分组汇总日消耗
        Map<LocalDate, Integer> dailyMap = new java.util.LinkedHashMap<>();
        for (OutboundHistory h : histories) {
            LocalDate d = h.getCreatedAt().toLocalDate();
            dailyMap.merge(d, h.getDeductQty() != null ? h.getDeductQty() : 0, Integer::sum);
        }
        // 填充无出库日为 0
        java.time.LocalDate cursor = thirtyDaysAgo.toLocalDate();
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Integer> dailyValues = new ArrayList<>();
        while (!cursor.isAfter(today)) {
            dailyValues.add(dailyMap.getOrDefault(cursor, 0));
            cursor = cursor.plusDays(1);
        }
        // 计算标准差 σ
        int n = dailyValues.size();
        double mean = dailyValues.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = dailyValues.stream()
            .mapToDouble(v -> (v - mean) * (v - mean))
            .sum() / n;
        double sigma = Math.sqrt(variance);
        // Z × σ × √LT
        double z = 1.65; // 95% service level
        int safety = (int) Math.ceil(z * sigma * Math.sqrt(Math.max(1, leadTimeDays)));
        return Math.max(0, safety);
    }

}
