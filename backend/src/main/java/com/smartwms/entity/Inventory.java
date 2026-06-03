/**
 * 物理实际库存记录实体（对应 inventories 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("inventories")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;

    /** 当前仓库实物库存现存量 */
    private Integer stockQty;

    /** 安全低储控制天数 */
    private Integer minStockDays;

    /** 安全高储积压控制天数 */
    private Integer maxStockDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }

    public Integer getMinStockDays() { return minStockDays; }
    public void setMinStockDays(Integer minStockDays) { this.minStockDays = minStockDays; }

    public Integer getMaxStockDays() { return maxStockDays; }
    public void setMaxStockDays(Integer maxStockDays) { this.maxStockDays = maxStockDays; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
