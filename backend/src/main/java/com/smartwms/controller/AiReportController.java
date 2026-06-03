/**
 * AI 库存预测与报告控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.smartwms.common.Result;
import com.smartwms.dto.AiPredictRequest;
import com.smartwms.entity.AiReport;
import com.smartwms.service.LLMIdentifyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiReportController {

    private final LLMIdentifyService llmIdentifyService;

    public AiReportController(LLMIdentifyService llmIdentifyService) {
        this.llmIdentifyService = llmIdentifyService;
    }

    /**
     * 触发物料库存风险 AI 预测（异步）。
     * POST /api/ai/predict
     */
    @PostMapping("/predict")
    public Result<AiReport> predict(@Valid @RequestBody AiPredictRequest request) {
        AiReport report = llmIdentifyService.triggerPredict(request.getMaterialCode());
        return Result.success("AI智能预测任务已成功在后台线程异步挂载启动", report);
    }

    /**
     * 查询指定物料的最新 AI 分析报告。
     * GET /api/ai/reports/latest?materialCode=M_PART_001
     */
    @GetMapping("/reports/latest")
    public Result<AiReport> getLatestReport(@RequestParam String materialCode) {
        AiReport report = llmIdentifyService.getLatestReport(materialCode);
        if (report == null) {
            return Result.error(404, "未找到该物料的 AI 分析报告");
        }
        return Result.success(report);
    }
}
