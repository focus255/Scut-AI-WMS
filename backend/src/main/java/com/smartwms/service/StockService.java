/**
 * 库存报表服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.StockReportVO;

public interface StockService {

    /**
     * 查询动态库存水位报表（分页），结合高低储天数进行内置规则评级。
     *
     * @param page         页码
     * @param size         每页条数
     * @param materialCode 物料号（可选模糊检索）
     * @param alarmStatus  水位状态过滤（可选：NORMAL / LOW / HIGH）
     * @return 库存报表分页视图
     */
    Page<StockReportVO> getStockReport(int page, int size, String materialCode, String alarmStatus);
}
