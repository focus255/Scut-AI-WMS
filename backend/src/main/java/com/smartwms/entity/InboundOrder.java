/**
 * 手工入库订单主表实体（对应 inbound_orders 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("inbound_orders")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手工入库单号（全局唯一） */
    private String orderNo;

    /** 入库状态：未入库 / 已完成 */
    private String status;

    /** 对应发货供应商 */
    private String supplierCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
