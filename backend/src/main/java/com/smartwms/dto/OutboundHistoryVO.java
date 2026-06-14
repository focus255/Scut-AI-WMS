package com.smartwms.dto;

import java.time.LocalDateTime;

/**
 * 出库流水视图对象。
 */
public class OutboundHistoryVO {

    private Long id;
    private String outboundOrderNo;
    private String materialCode;
    private String inboundOrderNo;
    private Long barcodeId;
    private String barcode;
    private Integer deductQty;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOutboundOrderNo() { return outboundOrderNo; }
    public void setOutboundOrderNo(String outboundOrderNo) { this.outboundOrderNo = outboundOrderNo; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getInboundOrderNo() { return inboundOrderNo; }
    public void setInboundOrderNo(String inboundOrderNo) { this.inboundOrderNo = inboundOrderNo; }
    public Long getBarcodeId() { return barcodeId; }
    public void setBarcodeId(Long barcodeId) { this.barcodeId = barcodeId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Integer getDeductQty() { return deductQty; }
    public void setDeductQty(Integer deductQty) { this.deductQty = deductQty; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
