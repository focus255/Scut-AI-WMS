/**
 * AI 智能预测服务——DeepSeek 大模型调用，含重试机制。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.AiReport;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.Material;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.mapper.AiReportMapper;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.service.AIService;
import com.smartwms.service.LLMIdentifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LLMIdentifyServiceImpl implements LLMIdentifyService {

    private static final Logger log = LoggerFactory.getLogger(LLMIdentifyServiceImpl.class);

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 1;
    /** 重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 2000;

    private final AiReportMapper aiReportMapper;
    private final InventoryMapper inventoryMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final BarcodeMapper barcodeMapper;
    private final MaterialMapper materialMapper;
    private final AIService aiService;

    public LLMIdentifyServiceImpl(AiReportMapper aiReportMapper,
                                   InventoryMapper inventoryMapper,
                                   OutboundHistoryMapper outboundHistoryMapper,
                                   BarcodeMapper barcodeMapper,
                                   MaterialMapper materialMapper,
                                   AIService aiService) {
        this.aiReportMapper = aiReportMapper;
        this.inventoryMapper = inventoryMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.materialMapper = materialMapper;
        this.aiService = aiService;
    }

    @Override
    @Transactional
    public AiReport triggerPredict(String materialCode) {
        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getMaterialCode, materialCode));
        if (inventory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在或未维护库存");
        }

        if (!aiService.isConfigured()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "AI 服务未配置，请设置 AI_LLM_API_KEY 环境变量");
        }

        AiReport report = new AiReport();
        report.setMaterialCode(materialCode);
        report.setCurrentStock(inventory.getStockQty());
        report.setRiskType("NORMAL");
        report.setRiskLevel("LOW");
        report.setAnalysisContent("分析中...");
        report.setReplenishmentSuggestion("分析中...");
        report.setSuggestedQty(0);
        report.setPredictionStatus("PENDING");
        report.setModel(aiService.getModelName());
        aiReportMapper.insert(report);

        log.info("[AI-Task] 启动物料 {} 异步推演, reportId={}", materialCode, report.getId());
        executeAsynchronousPredict(report.getId(), inventory.getId());
        return report;
    }

    @Override
    public AiReport getLatestReport(String materialCode) {
        return aiReportMapper.selectOne(
                new LambdaQueryWrapper<AiReport>()
                        .eq(AiReport::getMaterialCode, materialCode)
                        .orderByDesc(AiReport::getCreatedAt)
                        .last("LIMIT 1"));
    }

    @Override
    @Async("aiTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeAsynchronousPredict(Long reportId, Long materialId) {
        log.info("[AI-Task] 异步推演开始, reportId={}", reportId);

        AiReport report = aiReportMapper.selectById(reportId);
        if (report == null) return;

        report.setPredictionStatus("RUNNING");
        aiReportMapper.updateById(report);

        String materialCode = report.getMaterialCode();
        double dailyConsume = computeDailyConsume(materialCode);
        int idleDays = computeIdleDays(materialCode);
        Inventory inventory = inventoryMapper.selectById(materialId);

        // ====== 带重试的 LLM 调用 ======
        JsonNode aiResult = null;
        String lastError = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                log.info("[AI-Task] 第 {} 次重试, reportId={}", attempt, reportId);
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }

            try {
                aiResult = aiService.chat(
                        buildSystemPrompt(),
                        buildUserPrompt(materialCode, inventory, dailyConsume, idleDays));
                if (aiResult != null) break;
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("[AI-Task] 第 {} 次调用失败: {}", attempt + 1, lastError);
            }
        }

        // ====== 处理结果 ======
        if (aiResult != null) {
            fillFromLLMResponse(report, aiResult);
            report.setUpdatedAt(LocalDateTime.now());
            aiReportMapper.updateById(report);
            log.info("[AI-Success] 物料 {} 分析完成, 风险类型={}", materialCode, report.getRiskType());
        } else {
            report.setRiskType("NORMAL");
            report.setRiskLevel("LOW");
            report.setAnalysisContent("AI 分析暂时不可用：" + (lastError != null ? lastError : "网络连接失败") + "。请稍后重试。");
            report.setReplenishmentSuggestion("建议手动检查库存水位，或等待 AI 服务恢复后重新触发分析。");
            report.setSuggestedQty(0);
            report.setPredictionStatus("FAILED");
            report.setUpdatedAt(LocalDateTime.now());
            aiReportMapper.updateById(report);
            log.error("[AI-Failed] 物料 {} 分析失败, 错误={}", materialCode, lastError);
        }
    }

    /** 用 LLM 返回的 JSON 填充报告 */
    private void fillFromLLMResponse(AiReport report, JsonNode json) {
        report.setRiskType(json.path("riskType").asText("NORMAL"));
        report.setRiskLevel(json.path("riskLevel").asText("LOW"));
        report.setAnalysisContent(json.path("analysisContent").asText("分析内容解析失败"));
        report.setReplenishmentSuggestion(json.path("replenishmentSuggestion").asText(""));
        report.setSuggestedQty(json.path("suggestedQty").asInt(0));
        report.setPredictionStatus("SUCCESS");
    }

    // ==================== 提示词构建 ====================

    private String buildSystemPrompt() {
        return """
            你是一个专业的仓储库存分析师，服务于汽车零部件仓库。
            系统会提供物料的实时库存数据，你需要精准分析并给出建议。

            请严格按以下 JSON 格式返回（必须返回纯 JSON，不能包含 markdown 代码块标记）：
            {
              "riskType": "NORMAL|LOW_STOCK|HIGH|DEAD_STOCK|BOTH",
              "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
              "analysisContent": "库存状态分析（120字以内）",
              "replenishmentSuggestion": "补货建议或库存消化方案（100字以内）",
              "suggestedQty": 0
            }

            判定规则参考：
            - LOW_STOCK: 库存 < (日均消耗×提前期) + 安全库存
            - DEAD_STOCK: 超过90天无出库记录
            - HIGH: DOHF > 高储控制天数上限
            - BOTH: 同时存在低储和呆滞特征
            - 正常情况下返回 NORMAL
            """;
    }

    private String buildUserPrompt(String materialCode, Inventory inventory,
                                    double dailyConsume, int idleDays) {
        Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialCode, materialCode));

        int qty = inventory.getStockQty() != null ? inventory.getStockQty() : 0;
        int leadTime = inventory.getLeadTimeDays() != null ? inventory.getLeadTimeDays() : 7;
        int safetyStock = inventory.getSafetyStock() != null ? inventory.getSafetyStock() : 0;
        int maxDays = inventory.getMaxStockDays() != null ? inventory.getMaxStockDays() : 15;
        double lowThreshold = (dailyConsume * leadTime) + safetyStock;
        double dohf = dailyConsume > 0 ? qty / dailyConsume : 9999;

        return String.format("""
            分析以下物料库存数据：

            物料号: %s
            物料名称: %s
            当前库存: %d 件
            近30天日均消耗: %.1f 件/天
            DOHF(库存可维持天数): %.0f 天
            补货提前期: %d 天
            安全库存: %d 件
            补货预警线: %.0f 件
            高储控制上限: %d 天
            闲置天数(自最后出库): %d 天
            """,
            materialCode,
            material != null ? material.getMaterialName() : materialCode,
            qty, dailyConsume, dohf, leadTime, safetyStock,
            lowThreshold, maxDays, idleDays);
    }

    // ==================== 数据查询 ====================

    private double computeDailyConsume(String materialCode) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<OutboundHistory> histories = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getMaterialCode, materialCode)
                        .ge(OutboundHistory::getCreatedAt, thirtyDaysAgo));
        if (histories.isEmpty()) return 0.0;
        int total = histories.stream()
                .mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0).sum();
        return Math.max(0.0, (double) total / 30.0);
    }

    private int computeIdleDays(String materialCode) {
        OutboundHistory last = outboundHistoryMapper.selectOne(
                new LambdaQueryWrapper<OutboundHistory>()
                        .eq(OutboundHistory::getMaterialCode, materialCode)
                        .orderByDesc(OutboundHistory::getCreatedAt)
                        .last("LIMIT 1"));
        if (last != null) {
            return (int) ChronoUnit.DAYS.between(last.getCreatedAt(), LocalDateTime.now());
        }
        Barcode first = barcodeMapper.selectOne(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getMaterialCode, materialCode)
                        .eq(Barcode::getType, "inbound")
                        .orderByAsc(Barcode::getCreatedAt)
                        .last("LIMIT 1"));
        if (first != null) {
            return (int) ChronoUnit.DAYS.between(first.getCreatedAt(), LocalDateTime.now());
        }
        return 0;
    }
}
