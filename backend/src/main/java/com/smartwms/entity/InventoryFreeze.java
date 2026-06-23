/**
 * 库存封存记录实体（对应 inventory_freezes 表）。
 * 封存：暂停条码流转；解封：恢复为在库。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("inventory_freezes")
public class InventoryFreeze {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联条码 ID */
    private Long barcodeId;

    /** 物料编码（冗余，便于查询） */
    private String materialCode;

    /** 条码号 */
    private String barcode;

    /** 封存类型：QUALITY（质量问题）/ ADMIN（管理封存）/ OTHER */
    private String freezeType;

    /** 封存原因 */
    private String reason;

    /** 操作人 */
    private String operator;

    /** 封存时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime freezeTime;

    /** 解封时间 */
    private LocalDateTime unfreezeTime;

    /** 状态：FROZEN（封存中）/ UNFROZEN（已解封） */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBarcodeId() { return barcodeId; }
    public void setBarcodeId(Long barcodeId) { this.barcodeId = barcodeId; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getFreezeType() { return freezeType; }
    public void setFreezeType(String freezeType) { this.freezeType = freezeType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public LocalDateTime getFreezeTime() { return freezeTime; }
    public void setFreezeTime(LocalDateTime freezeTime) { this.freezeTime = freezeTime; }

    public LocalDateTime getUnfreezeTime() { return unfreezeTime; }
    public void setUnfreezeTime(LocalDateTime unfreezeTime) { this.unfreezeTime = unfreezeTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
