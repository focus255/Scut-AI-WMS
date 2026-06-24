/**
 * 出库单行项目明细实体（对应 outbound_details 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("outbound_details")
public class OutboundDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联主表 ID */
    private Long outboundId;

    /** 主单号 */
    private String orderNo;

    /** 物料号 */
    private String materialCode;

    /** 出库单器具容量快照 */
    private Integer packCapacity;

    /** 计划领料出库数量 */
    private Integer planQty;

    /** 仓库实际下架手工清点确认数量 */
    private Integer actualQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOutboundId() { return outboundId; }
    public void setOutboundId(Long outboundId) { this.outboundId = outboundId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public Integer getPackCapacity() { return packCapacity; }
    public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

    public Integer getPlanQty() { return planQty; }
    public void setPlanQty(Integer planQty) { this.planQty = planQty; }

    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
