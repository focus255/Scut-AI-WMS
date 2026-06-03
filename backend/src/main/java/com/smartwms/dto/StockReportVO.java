/**
 * 动态库存报表视图对象。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.dto;

import java.time.LocalDateTime;

public class StockReportVO {

    private String materialCode;
    private String materialName;
    private Integer stockQty;
    private Integer minStockDays;
    private Integer maxStockDays;
    private String ruleEvaluation;
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }

    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }

    public Integer getMinStockDays() { return minStockDays; }
    public void setMinStockDays(Integer minStockDays) { this.minStockDays = minStockDays; }

    public Integer getMaxStockDays() { return maxStockDays; }
    public void setMaxStockDays(Integer maxStockDays) { this.maxStockDays = maxStockDays; }

    public String getRuleEvaluation() { return ruleEvaluation; }
    public void setRuleEvaluation(String ruleEvaluation) { this.ruleEvaluation = ruleEvaluation; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
