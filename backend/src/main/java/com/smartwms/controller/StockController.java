/**
 * 库存报表与预警控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.smartwms.common.Result;
import com.smartwms.dto.StockReportVO;
import com.smartwms.service.StockService;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 查询动态库存水位报表（分页）。
     * GET /api/stock/report?materialCode=&alarmStatus=&page=&size=
     */
    @GetMapping("/report")
    public Result<Page<StockReportVO>> report(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String alarmStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(stockService.getStockReport(page, size, materialCode, alarmStatus));
    }
}
