/**
 * 器具包装参数实体（对应 appliances 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("appliances")
public class Appliance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;

    private String supplierCode;

    /** 包装器具型号（如小铁箱/塑料周转箱） */
    private String packType;

    /** 标准包装满载容量数量 */
    private Integer packCapacity;

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

    public String getPackType() { return packType; }
    public void setPackType(String packType) { this.packType = packType; }

    public Integer getPackCapacity() { return packCapacity; }
    public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
