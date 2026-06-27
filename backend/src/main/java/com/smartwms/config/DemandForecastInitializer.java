/**
 * 启动时检测需求预测数据，无数据则自动为全部物料生成。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.config;

import com.smartwms.entity.Inventory;
import com.smartwms.mapper.DemandForecastMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.service.DemandForecastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemandForecastInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemandForecastInitializer.class);

    private final DemandForecastMapper forecastMapper;
    private final InventoryMapper inventoryMapper;
    private final DemandForecastService forecastService;

    public DemandForecastInitializer(DemandForecastMapper forecastMapper,
                                      InventoryMapper inventoryMapper,
                                      DemandForecastService forecastService) {
        this.forecastMapper = forecastMapper;
        this.inventoryMapper = inventoryMapper;
        this.forecastService = forecastService;
    }

    @Override
    public void run(String... args) {
        // 清理脏数据：删除 materialCode 为 generate-all 等无效记录的预测
        int cleaned = forecastMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.smartwms.entity.DemandForecast>()
                .eq(com.smartwms.entity.DemandForecast::getMaterialCode, "generate-all")
        );
        if (cleaned > 0) {
            log.info("[需求预测] 已清理 {} 条无效预测记录 (materialCode=generate-all)", cleaned);
        }

        if (forecastMapper.selectCount(null) > 0) {
            log.info("[需求预测] 已有预测数据，跳过自动生成");
            return;
        }

        List<Inventory> inventories = inventoryMapper.selectList(null);
        if (inventories.isEmpty()) {
            log.info("[需求预测] 无库存数据，跳过");
            return;
        }

        log.info("[需求预测] 未找到预测数据，开始为 {} 种物料自动生成...", inventories.size());
        int done = 0;
        for (Inventory inv : inventories) {
            try {
                forecastService.generate(inv.getMaterialCode());
                done++;
                log.info("[需求预测] {}/{} {}", done, inventories.size(), inv.getMaterialCode());
            } catch (Exception e) {
                log.warn("[需求预测] {} 失败: {}", inv.getMaterialCode(), e.getMessage());
            }
        }
        log.info("[需求预测] 全部完成，共 {} 条", done);
    }
}
