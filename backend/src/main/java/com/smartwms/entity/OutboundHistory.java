package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 出库批次流水实体。
 */
@TableName("outbound_histories")
public class OutboundHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long outboundId;
    private String outboundOrderNo;
    private Long outboundDetailId;
    private String materialCode;
    private Long inboundId;
    private String inboundOrderNo;
    private Long inboundDetailId;
    private Long barcodeId;
    private String barcode;
    private Integer deductQty;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOutboundId() { return outboundId; }
    public void setOutboundId(Long outboundId) { this.outboundId = outboundId; }
    public String getOutboundOrderNo() { return outboundOrderNo; }
    public void setOutboundOrderNo(String outboundOrderNo) { this.outboundOrderNo = outboundOrderNo; }
    public Long getOutboundDetailId() { return outboundDetailId; }
    public void setOutboundDetailId(Long outboundDetailId) { this.outboundDetailId = outboundDetailId; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public Long getInboundId() { return inboundId; }
    public void setInboundId(Long inboundId) { this.inboundId = inboundId; }
    public String getInboundOrderNo() { return inboundOrderNo; }
    public void setInboundOrderNo(String inboundOrderNo) { this.inboundOrderNo = inboundOrderNo; }
    public Long getInboundDetailId() { return inboundDetailId; }
    public void setInboundDetailId(Long inboundDetailId) { this.inboundDetailId = inboundDetailId; }
    public Long getBarcodeId() { return barcodeId; }
    public void setBarcodeId(Long barcodeId) { this.barcodeId = barcodeId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Integer getDeductQty() { return deductQty; }
    public void setDeductQty(Integer deductQty) { this.deductQty = deductQty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
