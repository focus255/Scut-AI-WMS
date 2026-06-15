/**
 * 物料器具条码追踪实体（对应 barcodes 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("barcodes")
public class Barcode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 零件编码 */
    private String materialCode;

    /** 生产供应商 / OUT（出库标签时） */
    private String supplierCode;

    /** 唯一箱单标签条码号 */
    private String barcode;

    /** 来源单据 ID：入库条码时为入库单 ID，出库标签时为出库单 ID（字段名 inboundId 因历史原因保留） */
    private Long inboundId;

    /** 条码类型：inbound（入库条码）/ outbound（出库标签） */
    private String type;

    /** 条码生命周期：待入库/在库/已出库（入库）；待出库/已出库（出库） */
    private String status;

    /** 当前剩余数量（入库条码拆箱后余量，出库标签为单箱数量） */
    private Integer remainingQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public Long getInboundId() { return inboundId; }
    public void setInboundId(Long inboundId) { this.inboundId = inboundId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRemainingQty() { return remainingQty; }
    public void setRemainingQty(Integer remainingQty) { this.remainingQty = remainingQty; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
