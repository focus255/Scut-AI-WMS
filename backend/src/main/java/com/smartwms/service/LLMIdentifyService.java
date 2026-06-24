/**
 * AI 智能预测服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.smartwms.entity.AiReport;

public interface LLMIdentifyService {

    /**
     * 触发物料的异步 AI 库存风险预测。
     * 先创建 PENDING 状态的报告记录，再提交异步任务执行推演。
     *
     * @param materialCode 物料号
     * @return 新创建的 AI 报告记录（含报告 ID 和 PENDING 状态）
     */
    AiReport triggerPredict(String materialCode);

    /**
     * 获取指定物料的最新 AI 分析报告。
     */
    AiReport getLatestReport(String materialCode);

    /**
     * 异步执行 AI 预测（由线程池调度，不应在控制器线程中直接调用）。
     * 流程：RUNNING → 调 LLM → SUCCESS / MOCKED
     *
     * @param reportId   AI 报告 ID
     * @param materialId 物料 ID
     */
    void executeAsynchronousPredict(Long reportId, Long materialId);
}
