/**
 * 入库单行项目明细实体（对应 inbound_details 表）。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("inbound_details")
public class InboundDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联主表自增 ID */
    private Long inboundId;

    /** 关联业务单号 */
    private String orderNo;

    /** 物料号 */
    private String materialCode;

    /** 包装容量快照 */
    private Integer packCapacity;

    /** 计划入库总数 */
    private Integer planQty;

    /** 到货现场手工确认实际核销数量 */
    private Integer actualQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInboundId() { return inboundId; }
    public void setInboundId(Long inboundId) { this.inboundId = inboundId; }

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
