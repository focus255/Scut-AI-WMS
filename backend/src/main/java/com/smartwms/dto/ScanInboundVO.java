/**
 * 扫码入库响应 VO。
 *
 * @author Focus
 * @date 2026-06-10
 */
package com.smartwms.dto;

import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;

public class ScanInboundVO {

    private String barcode;
    private String materialCode;
    private String supplierCode;
    private String status;
    private String orderNo;
    private Integer packCapacity;
    private Boolean confirmed;

    /**
     * 从条码和明细组装扫码入库结果。
     */
    public static ScanInboundVO from(Barcode bc, InboundDetail detail, boolean confirmed) {
        ScanInboundVO vo = new ScanInboundVO();
        vo.setBarcode(bc.getBarcode());
        vo.setMaterialCode(bc.getMaterialCode());
        vo.setSupplierCode(bc.getSupplierCode());
        vo.setStatus(bc.getStatus());
        vo.setConfirmed(confirmed);
        if (detail != null) {
            vo.setOrderNo(detail.getOrderNo());
            vo.setPackCapacity(detail.getPackCapacity());
        }
        return vo;
    }

    // ==================== Getters / Setters ====================

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Integer getPackCapacity() { return packCapacity; }
    public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
}
