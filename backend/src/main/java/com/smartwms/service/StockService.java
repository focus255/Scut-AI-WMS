/**
 * 库存报表服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.smartwms.dto.StockReportVO;
import java.util.List;

public interface StockService {

    /**
     * 查询动态库存水位报表，结合高低储天数进行内置规则评级。
     *
     * @param materialCode 物料号（可选模糊检索）
     * @param alarmStatus  水位状态过滤（可选：NORMAL / LOW / HIGH）
     * @return 库存报表视图列表
     */
    List<StockReportVO> getStockReport(String materialCode, String alarmStatus);
}
