/**
 * AI 库存推演与智能决策报告实体（对应 ai_inventory_reports 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("ai_inventory_reports")
public class AiReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预测目标物料号 */
    private String materialCode;

    /** 预测切片时的物理库存数量快照 */
    private Integer currentStock;

    /** 大模型研判类型：NORMAL / LOW_STOCK / DEAD_STOCK / BOTH */
    private String riskType;

    /** 风险等级：LOW / MEDIUM / HIGH / CRITICAL */
    private String riskLevel;

    /** AI 生成的核心库存演进与根因剖析大段文字 */
    private String analysisContent;

    /** AI 给出的物料精益补货控制行动计划描述 */
    private String replenishmentSuggestion;

    /** AI 给出的量化推荐补货量（件） */
    private Integer suggestedQty;

    /** 诊断状态：PENDING / RUNNING / SUCCESS / FAILED */
    private String predictionStatus;

    /** 生成报告的模型名称（如 deepseek-v4-flash） */
    private String model;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public Integer getCurrentStock() { return currentStock; }
    public void setCurrentStock(Integer currentStock) { this.currentStock = currentStock; }

    public String getRiskType() { return riskType; }
    public void setRiskType(String riskType) { this.riskType = riskType; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getAnalysisContent() { return analysisContent; }
    public void setAnalysisContent(String analysisContent) { this.analysisContent = analysisContent; }

    public String getReplenishmentSuggestion() { return replenishmentSuggestion; }
    public void setReplenishmentSuggestion(String replenishmentSuggestion) { this.replenishmentSuggestion = replenishmentSuggestion; }

    public Integer getSuggestedQty() { return suggestedQty; }
    public void setSuggestedQty(Integer suggestedQty) { this.suggestedQty = suggestedQty; }

    public String getPredictionStatus() { return predictionStatus; }
    public void setPredictionStatus(String predictionStatus) { this.predictionStatus = predictionStatus; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
