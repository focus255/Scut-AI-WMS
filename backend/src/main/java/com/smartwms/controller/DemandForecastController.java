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
        return Result.success(service.generate(materialCode));
    }
}
