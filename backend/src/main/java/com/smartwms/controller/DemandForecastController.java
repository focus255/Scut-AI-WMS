package com.smartwms.controller;

import com.smartwms.common.Result;
import com.smartwms.entity.DemandForecast;
import com.smartwms.service.DemandForecastService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/demand")
public class DemandForecastController {

    private final DemandForecastService service;

    public DemandForecastController(DemandForecastService service) {
        this.service = service;
    }

    /** 获取所有需求预测 */
    @GetMapping("/forecasts")
    public Result<List<DemandForecast>> getAll() {
        return Result.success(service.getAll());
    }

    /** 重新生成单个物料预测 */
    @PostMapping("/forecasts/{materialCode}")
    public Result<DemandForecast> regenerate(@PathVariable String materialCode) {
        // 防止 generate-all 等 API 路径名被误当作物料号
        if ("generate-all".equals(materialCode)) {
            return Result.error(400, "无效的物料编码: generate-all");
        }
        return Result.success(service.generate(materialCode));
    }

    /**
     * 批量生成全部物料的需求预测。
     *
     * @author Focus
     * @date 2026-06-25
     */
    @PostMapping("/forecasts/generate-all")
    public Result<Integer> generateAll() {
        return Result.success(service.generateAll());
    }
}
