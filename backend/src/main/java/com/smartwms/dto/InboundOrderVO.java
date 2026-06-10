/**
 * 入库单详情 VO，包含主表信息与明细行列表。
 *
 * @author Claude
 * @date 2026-06-10
 */
package com.smartwms.dto;

import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;

import java.util.Collections;
import java.util.List;

public class InboundOrderVO {

    private Long id;
    private String orderNo;
    private String status;
    private String supplierCode;
    private String createdAt;
    private String updatedAt;
    private List<InboundDetail> details;
    private List<Barcode> barcodes;

    /**
     * 从 InboundOrder 实体构造 VO（含明细行与条码列表）。
     */
    public static InboundOrderVO from(InboundOrder order, List<InboundDetail> details, List<Barcode> barcodes) {
        InboundOrderVO vo = new InboundOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setSupplierCode(order.getSupplierCode());
        vo.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        vo.setUpdatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
        vo.setDetails(details);
        vo.setBarcodes(barcodes != null ? barcodes : Collections.emptyList());
        return vo;
    }

    // ==================== Getters / Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<InboundDetail> getDetails() { return details; }
    public void setDetails(List<InboundDetail> details) { this.details = details; }

    public List<Barcode> getBarcodes() { return barcodes; }
    public void setBarcodes(List<Barcode> barcodes) { this.barcodes = barcodes; }
}
