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
    private Integer safetyStock;
    private Integer leadTimeDays;
    private String ruleEvaluation;
    /** 近30天日均消耗量（件/天） */
    private Double dailyConsume;
    /** 库存未来持有天数 DOHF = stockQty / dailyConsume */
    private Double dohf;
    /** 最后一次出库日期 */
    private LocalDateTime lastOutboundDate;
    /** 闲置天数（自最后出库至今） */
    private Integer idleDays;
    /** 算法推荐安全库存量（基于历史需求波动计算） */
    private Integer suggestedSafetyStock;
    /** 算法推荐补货提前期（基于历史入库间隔计算） */
    private Integer suggestedLeadTimeDays;
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

    public Integer getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Integer safetyStock) { this.safetyStock = safetyStock; }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public String getRuleEvaluation() { return ruleEvaluation; }
    public void setRuleEvaluation(String ruleEvaluation) { this.ruleEvaluation = ruleEvaluation; }

    public Double getDailyConsume() { return dailyConsume; }
    public void setDailyConsume(Double dailyConsume) { this.dailyConsume = dailyConsume; }

    public Double getDohf() { return dohf; }
    public void setDohf(Double dohf) { this.dohf = dohf; }

    public LocalDateTime getLastOutboundDate() { return lastOutboundDate; }
    public void setLastOutboundDate(LocalDateTime lastOutboundDate) { this.lastOutboundDate = lastOutboundDate; }

    public Integer getIdleDays() { return idleDays; }
    public void setIdleDays(Integer idleDays) { this.idleDays = idleDays; }

    public Integer getSuggestedSafetyStock() { return suggestedSafetyStock; }
    public void setSuggestedSafetyStock(Integer suggestedSafetyStock) { this.suggestedSafetyStock = suggestedSafetyStock; }

    public Integer getSuggestedLeadTimeDays() { return suggestedLeadTimeDays; }
    public void setSuggestedLeadTimeDays(Integer suggestedLeadTimeDays) { this.suggestedLeadTimeDays = suggestedLeadTimeDays; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
