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

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * 查询动态库存水位报表。
     * GET /api/stock/report?materialCode=&alarmStatus=
     */
    @GetMapping("/report")
    public Result<List<StockReportVO>> report(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String alarmStatus) {
        return Result.success(stockService.getStockReport(materialCode, alarmStatus));
    }
}
