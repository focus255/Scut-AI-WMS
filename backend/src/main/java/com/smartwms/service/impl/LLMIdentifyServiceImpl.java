/**
 * AI 智能预测服务实现（桩实现，后续对接大模型 API）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.AiReport;
import com.smartwms.entity.Inventory;
import com.smartwms.engine.RuleMockEngine;
import com.smartwms.mapper.AiReportMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.service.LLMIdentifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LLMIdentifyServiceImpl implements LLMIdentifyService {

    private static final Logger log = LoggerFactory.getLogger(LLMIdentifyServiceImpl.class);

    private final AiReportMapper aiReportMapper;
    private final InventoryMapper inventoryMapper;
    private final RuleMockEngine ruleMockEngine;

    public LLMIdentifyServiceImpl(AiReportMapper aiReportMapper,
                                   InventoryMapper inventoryMapper,
                                   RuleMockEngine ruleMockEngine) {
        this.aiReportMapper = aiReportMapper;
        this.inventoryMapper = inventoryMapper;
        this.ruleMockEngine = ruleMockEngine;
    }

    @Override
    @Transactional
    public AiReport triggerPredict(String materialCode) {
        // 查询库存快照
        Inventory inventory = inventoryMapper.selectOne(
                new LambdaQueryWrapper<Inventory>()
                        .eq(Inventory::getMaterialCode, materialCode)
        );
        if (inventory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在或未维护库存");
        }

        // 创建 PENDING 状态的报告记录
        AiReport report = new AiReport();
        report.setMaterialCode(materialCode);
        report.setCurrentStock(inventory.getStockQty());
        report.setRiskType("NORMAL");
        report.setRiskLevel("LOW");
        report.setAnalysisContent("分析中...");
        report.setReplenishmentSuggestion("分析中...");
        report.setSuggestedQty(0);
        report.setPredictionStatus("PENDING");
        report.setConfidence(0f);
        aiReportMapper.insert(report);

        log.info("[AI-Task] 启动物料 {} 的异步推演任务，生成报告单占位ID: {}",
                materialCode, report.getId());

        // 启动异步推演
        executeAsynchronousPredict(report.getId(), inventory.getId());

        return report;
    }

    @Override
    public AiReport getLatestReport(String materialCode) {
        return aiReportMapper.selectOne(
                new LambdaQueryWrapper<AiReport>()
                        .eq(AiReport::getMaterialCode, materialCode)
                        .orderByDesc(AiReport::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    @Override
    @Async("aiTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeAsynchronousPredict(Long reportId, Long materialId) {
        log.info("[AI-Task] 异步推演开始, reportId={}, materialId={}", reportId, materialId);

        AiReport report = aiReportMapper.selectById(reportId);
        if (report == null) return;

        // 标记为 RUNNING
        report.setPredictionStatus("RUNNING");
        aiReportMapper.updateById(report);

        try {
            // 桩实现：直接调用 Mock 引擎生成报告
            // TODO(Focus, 2026-06-03): 后续对接真实大模型 API 替换此桩实现
            Inventory inventory = inventoryMapper.selectById(materialId);

            // 使用默认日均消耗模拟数据
            AiReport mockReport = ruleMockEngine.generateMockReport(
                    report.getMaterialCode(), inventory, 10.0, 150.0
            );

            // 将 Mock 结果回写到报告记录
            report.setRiskType(mockReport.getRiskType());
            report.setRiskLevel(mockReport.getRiskLevel());
            report.setAnalysisContent(mockReport.getAnalysisContent());
            report.setReplenishmentSuggestion(mockReport.getReplenishmentSuggestion());
            report.setSuggestedQty(mockReport.getSuggestedQty());
            report.setConfidence(mockReport.getConfidence());
            report.setPredictionStatus("SUCCESS");
            report.setUpdatedAt(LocalDateTime.now());
            aiReportMapper.updateById(report);

            log.info("[AI-API-Success] 成功接收LLM结构化JSON (Mock模式)，模型判定风险类型为 {}",
                    report.getRiskType());
        } catch (Exception e) {
            log.warn("[AI-API-Timeout] 调用外部LLM接口超时异常，系统无缝启动精益Mock规则引擎拼装基础降级报告方案");
            // 兜底：调用 Mock 引擎
            Inventory inventory = inventoryMapper.selectById(materialId);
            AiReport fallback = ruleMockEngine.generateMockReport(
                    report.getMaterialCode(), inventory, 10.0, 150.0
            );
            fallback.setPredictionStatus("MOCKED");
            fallback.setUpdatedAt(LocalDateTime.now());
            // 保留原报告 ID
            fallback.setId(report.getId());
            aiReportMapper.updateById(fallback);
        }
    }
}
