/**
 * 规则降级 Mock 引擎，当大模型 API 不可用时提供本地规则兜底。
 * 基于动态平衡公式进行四级库存评级：呆滞 / 低储 / 高储 / 正常。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.engine;

import com.smartwms.entity.AiReport;
import com.smartwms.entity.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RuleMockEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleMockEngine.class);

    /** 呆滞判定阈值：超过此天数无出库即视为呆滞 */
    private static final int DEAD_STOCK_DAYS = 90;

    /**
     * 根据本地规则生成降级 Mock 报告，覆盖四级评级。
     *
     * @param materialCode 物料号
     * @param inventory    当前库存快照
     * @param dailyConsume 近 30 日日均消耗量
     * @param futureDemand 未来 15 天预测总需求
     * @param idleDays     闲置天数（自最后出库至今），-1 表示未计算
     * @return 填充了 Mock 数据的 AiReport（状态应设为 MOCKED）
     */
    public AiReport generateMockReport(String materialCode, Inventory inventory,
                                        double dailyConsume, double futureDemand, int idleDays) {
        log.warn("[Mock引擎] 启动降级规则引擎，物料={}, 当前库存={}, 日均消耗={}, 闲置天数={}",
                materialCode, inventory.getStockQty(), dailyConsume, idleDays);

        AiReport report = new AiReport();
        report.setMaterialCode(materialCode);
        report.setCurrentStock(inventory.getStockQty());
        report.setPredictionStatus("MOCKED");

        int currentStock = inventory.getStockQty() != null ? inventory.getStockQty() : 0;
        int minStockDays = inventory.getMinStockDays() != null ? inventory.getMinStockDays() : 3;
        int maxStockDays = inventory.getMaxStockDays() != null ? inventory.getMaxStockDays() : 15;
        int leadTimeDays = inventory.getLeadTimeDays() != null ? inventory.getLeadTimeDays() : 7;
        int safetyStock = inventory.getSafetyStock() != null ? inventory.getSafetyStock() : 0;

        // 补货预警线 = (日均销量 × 补货提前期) + 安全库存
        double lowThreshold = (dailyConsume * leadTimeDays) + safetyStock;

        // 优先级判定
        if (currentStock <= 0) {
            buildZeroStockReport(report, currentStock, dailyConsume, lowThreshold);
        } else if (idleDays >= DEAD_STOCK_DAYS) {
            buildDeadStockReport(report, currentStock, idleDays, dailyConsume, maxStockDays);
        } else if (dailyConsume > 0 && currentStock < lowThreshold) {
            buildLowStockReport(report, currentStock, dailyConsume, futureDemand,
                    lowThreshold, minStockDays, leadTimeDays, safetyStock);
        } else if (dailyConsume > 0) {
            double dohf = currentStock / dailyConsume;
            if (dohf > maxStockDays) {
                buildHighStockReport(report, currentStock, dailyConsume, dohf, maxStockDays);
            } else {
                buildNormalReport(report, currentStock, dailyConsume, lowThreshold, maxStockDays);
            }
        } else {
            buildNormalReport(report, currentStock, dailyConsume, lowThreshold, maxStockDays);
        }

        return report;
    }

    /**
     * 兼容旧调用签名（无 idleDays 参数）。
     */
    public AiReport generateMockReport(String materialCode, Inventory inventory,
                                        double dailyConsume, double futureDemand) {
        return generateMockReport(materialCode, inventory, dailyConsume, futureDemand, -1);
    }

    // ==================== 各评级报告构建方法 ====================

    private void buildZeroStockReport(AiReport report, int currentStock, double dailyConsume,
                                       double lowThreshold) {
        report.setRiskType("LOW_STOCK");
        report.setRiskLevel("CRITICAL");
        int gap = (int) Math.ceil(lowThreshold);
        report.setSuggestedQty(Math.max(gap, 1));
        report.setAnalysisContent(
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                "该物料当前库存为0，已完全断货，属于最高优先级紧急预警。补货预警线为" + (int) lowThreshold + "件。"
        );
        report.setReplenishmentSuggestion(
                "建议立即向供应商发起紧急补货订单。推荐补货量：" + report.getSuggestedQty() +
                "件，至少恢复至补货预警线以上。"
        );
    }

    private void buildDeadStockReport(AiReport report, int currentStock, int idleDays,
                                       double dailyConsume, int maxStockDays) {
        report.setRiskType("DEAD_STOCK");
        // 根据闲置时长分级：90-180天→MEDIUM, 180+天→HIGH
        report.setRiskLevel(idleDays >= 180 ? "HIGH" : "MEDIUM");
        report.setSuggestedQty(0);
        double dohf = dailyConsume > 0 ? currentStock / dailyConsume : 9999;
        report.setAnalysisContent(
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                "该物料已连续" + idleDays + "天无出库记录，超过呆滞判定阈值（" + DEAD_STOCK_DAYS + "天），" +
                "当前库存" + currentStock + "件，存在严重的资金占用与物料过期风险。" +
                (dailyConsume > 0 ? "DOHF约" + (int) dohf + "天，远超控制上限" + maxStockDays + "天。" : "")
        );
        report.setReplenishmentSuggestion(
                "建议立即暂停该物料采购计划，优先消耗现有库存。可考虑与供应商协商退货、调拨至其他工厂，" +
                "或启动折价处理流程。建立库存消化跟踪机制，每月复核呆滞状态。"
        );
    }

    private void buildLowStockReport(AiReport report, int currentStock, double dailyConsume,
                                      double futureDemand, double lowThreshold,
                                      int minStockDays, int leadTimeDays, int safetyStock) {
        report.setRiskType("LOW_STOCK");

        // 按未来需求与当前库存的缺口计算建议补货量
        int gap = (int) Math.ceil(futureDemand - currentStock);
        if (gap <= 0) {
            gap = (int) Math.ceil(lowThreshold - currentStock);
        }
        report.setSuggestedQty(Math.max(gap, 0));

        // 按可维持天数划分紧急程度
        int sustainDays = (int) (currentStock / Math.max(dailyConsume, 0.01));
        report.setRiskLevel(sustainDays <= 1 ? "CRITICAL" : sustainDays <= 3 ? "HIGH" : "MEDIUM");

        report.setAnalysisContent(
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                "当前库存(" + currentStock + "件)已跌破补货预警线(" + (int) lowThreshold + "件)，" +
                "预警线=(日均" + String.format("%.1f", dailyConsume) + "件×提前期" + leadTimeDays +
                "天)+安全库存" + safetyStock + "件。按当前消耗速度仅可维持约" + sustainDays + "天，" +
                "预计未来需求存在供应缺口，产生断供风险。"
        );
        report.setReplenishmentSuggestion(
                "建议向供应商发起紧急补货。推荐补货量：" + report.getSuggestedQty() +
                "件，可将库存水位恢复至补货预警线以上。建议同步评估安全库存参数是否合理。"
        );
    }

    private void buildHighStockReport(AiReport report, int currentStock, double dailyConsume,
                                       double dohf, int maxStockDays) {
        report.setRiskType("DEAD_STOCK");  // 高储与呆滞共用 DEAD_STOCK 风险类型
        report.setRiskLevel(dohf > maxStockDays * 2 ? "HIGH" : "MEDIUM");
        report.setSuggestedQty(0);

        report.setAnalysisContent(
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                "当前库存(" + currentStock + "件)的DOHF约" + (int) dohf + "天，远超最高控制线" +
                maxStockDays + "天。存在资金过度占用与潜在呆滞积压风险。日均消耗仅" +
                String.format("%.1f", dailyConsume) + "件，现有库存可支撑异常长的周期。"
        );
        report.setReplenishmentSuggestion(
                "建议暂缓该物料采购计划，优先消耗现有库存（当前库存=" + currentStock + "件，" +
                "高储阈值DOHF=" + maxStockDays + "天，实际DOHF=" + (int) dohf + "天）。" +
                "可考虑调整maxStockDays参数或启动库存瘦身计划。"
        );
    }

    private void buildNormalReport(AiReport report, int currentStock, double dailyConsume,
                                    double lowThreshold, int maxStockDays) {
        report.setRiskType("NORMAL");
        report.setRiskLevel("LOW");
        report.setSuggestedQty(0);
        double dohf = dailyConsume > 0 ? currentStock / dailyConsume : 0;
        report.setAnalysisContent(
                "[降级引擎Mock提示]: 基于本地规则扫描，当前库存水位处于正常范围。" +
                "库存" + currentStock + "件，补货预警线" + (int) lowThreshold + "件，" +
                (dailyConsume > 0 ? "DOHF约" + (int) dohf + "天(上限" + maxStockDays + "天)，" : "") +
                "暂无断供或积压风险。"
        );
        report.setReplenishmentSuggestion("维持当前库存水位，按正常计划执行采购。建议每周例行复核。");
    }
}
