/**
 * 规则降级 Mock 引擎，当大模型 API 不可用时提供本地规则兜底。
 * 基于 PRD 3.3 节的高低储计算公式生成基础库存建议。
 *
 * @author Focus
 * @date 2026-06-03
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

    /**
     * 根据本地高低储规则生成降级 Mock 报告。
     *
     * @param materialCode 物料编码
     * @param inventory    当前库存快照
     * @param dailyConsume 近 30 日日均消耗量
     * @param futureDemand 未来 15 天预测总需求
     * @return 填充了 Mock 数据的 AiReport（状态应设为 MOCKED）
     */
    public AiReport generateMockReport(String materialCode, Inventory inventory,
                                        double dailyConsume, double futureDemand) {
        log.warn("[Mock引擎] 启动降级规则引擎，物料={}, 当前库存={}, 日均消耗={}",
                materialCode, inventory.getStockQty(), dailyConsume);

        AiReport report = new AiReport();
        report.setMaterialCode(materialCode);
        report.setCurrentStock(inventory.getStockQty());
        report.setPredictionStatus("MOCKED");
        report.setConfidence(0.6f);

        double lowStockThreshold = dailyConsume * inventory.getMinStockDays();
        double highStockThreshold = dailyConsume * inventory.getMaxStockDays();
        int currentStock = inventory.getStockQty();

        // 规则判断：低储风险
        if (dailyConsume > 0 && currentStock < lowStockThreshold) {
            report.setRiskType("LOW_STOCK");

            // 按未来需求与当前库存的缺口计算建议补货量
            int gap = (int) Math.ceil(futureDemand - currentStock);
            if (gap <= 0) {
                // 仅低于低储线但未来需求不大，按低储缺口补足
                gap = (int) Math.ceil(lowStockThreshold - currentStock);
            }
            report.setSuggestedQty(Math.max(gap, 0));

            int urgency = (int) (currentStock / Math.max(dailyConsume, 0.01));
            report.setRiskLevel(urgency <= 1 ? "CRITICAL" : urgency <= 3 ? "HIGH" : "MEDIUM");

            report.setAnalysisContent(
                    "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                    "当前库存已跌破低储天数（" + inventory.getMinStockDays() + "天）标准线，" +
                    "预计未来需求存在供应缺口（当前库存=" + currentStock + "件，" +
                    "低储阈值=" + (int) lowStockThreshold + "件），产生基础断供风险。"
            );
            report.setReplenishmentSuggestion(
                    "建议向供应商发起紧急补货。推荐补货量：" + report.getSuggestedQty() +
                    "件，可将库存水位恢复至低储安全线以上。"
            );
        }
        // 规则判断：高储积压风险
        else if (dailyConsume > 0 && currentStock > highStockThreshold) {
            report.setRiskType("DEAD_STOCK");
            report.setRiskLevel("MEDIUM");
            report.setSuggestedQty(0);

            report.setAnalysisContent(
                    "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                    "当前库存已超过高储天数（" + inventory.getMaxStockDays() + "天）标准线，" +
                    "存在资金占用与呆滞风险。"
            );
            report.setReplenishmentSuggestion(
                    "建议暂缓该物料采购，优先消耗现有库存（当前库存=" + currentStock + "件，" +
                    "高储阈值=" + (int) highStockThreshold + "件）。"
            );
        }
        // 正常水位
        else {
            report.setRiskType("NORMAL");
            report.setRiskLevel("LOW");
            report.setSuggestedQty(0);
            report.setAnalysisContent(
                    "[降级引擎Mock提示]: 基于本地规则扫描，当前库存水位处于正常范围，暂无断供或积压风险。"
            );
            report.setReplenishmentSuggestion("维持当前库存水位，按正常计划执行采购。");
        }

        return report;
    }
}
