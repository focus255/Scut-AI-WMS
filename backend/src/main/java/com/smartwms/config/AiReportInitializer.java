/**
 * 应用启动时自动为所有物料生成 AI 报告（仅当不存在任何报告时）。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.config;

import com.smartwms.entity.Inventory;
import com.smartwms.mapper.AiReportMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.service.LLMIdentifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiReportInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AiReportInitializer.class);

    private final AiReportMapper aiReportMapper;
    private final InventoryMapper inventoryMapper;
    private final LLMIdentifyService llmIdentifyService;

    public AiReportInitializer(AiReportMapper aiReportMapper,
                                InventoryMapper inventoryMapper,
                                LLMIdentifyService llmIdentifyService) {
        this.aiReportMapper = aiReportMapper;
        this.inventoryMapper = inventoryMapper;
        this.llmIdentifyService = llmIdentifyService;
    }

    @Override
    public void run(String... args) {
        // 如果已有报告，跳过自动生成
        if (aiReportMapper.selectCount(null) > 0) {
            log.info("[AI初始化] 已有 AI 报告，跳过自动生成");
            return;
        }

        List<Inventory> inventories = inventoryMapper.selectList(null);
        if (inventories.isEmpty()) {
            log.info("[AI初始化] 无库存数据，跳过");
            return;
        }

        log.info("[AI初始化] 未找到 AI 报告，开始为 {} 种物料自动生成...", inventories.size());
        for (Inventory inv : inventories) {
            try {
                llmIdentifyService.triggerPredict(inv.getMaterialCode());
                log.info("[AI初始化] 已提交物料 {} 的 AI 分析", inv.getMaterialCode());
                Thread.sleep(500); // 间隔 0.5 秒，避免 API 限流
            } catch (Exception e) {
                log.warn("[AI初始化] 物料 {} 提交失败: {}", inv.getMaterialCode(), e.getMessage());
            }
        }
        log.info("[AI初始化] 全部物料已提交 AI 分析");
    }
}
