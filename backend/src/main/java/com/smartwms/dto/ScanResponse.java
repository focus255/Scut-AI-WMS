package com.smartwms.dto;

/**
 * 统一扫码响应 VO，前端根据 type 字段区分入库/出库结果。
 *
 * @author Focus
 * @date 2026-06-15
 */
public class ScanResponse {

    /** 扫码类型：inbound（入库）/ outbound（出库） */
    private String type;

    /** 物料号 */
    private String materialCode;

    /** 供应商编码（入库时有值，出库时为 "OUT"） */
    private String supplierCode;

    /** 关联单号（入库单号或出库单号） */
    private String orderNo;

    /** 本次操作数量 */
    private Integer qty;

    /** 看板号 */
    private String barcode;

    // ==================== 静态工厂 ====================

    public static ScanResponse inbound(ScanInboundVO vo) {
        ScanResponse r = new ScanResponse();
        r.type = "inbound";
        r.materialCode = vo.getMaterialCode();
        r.supplierCode = vo.getSupplierCode();
        r.orderNo = vo.getOrderNo();
        r.qty = vo.getPackCapacity() != null ? vo.getPackCapacity() : 0;
        r.barcode = vo.getBarcode();
        return r;
    }

    public static ScanResponse outbound(String orderNo, String materialCode,
                                         String barcode, int qty) {
        ScanResponse r = new ScanResponse();
        r.type = "outbound";
        r.materialCode = materialCode;
        r.supplierCode = "OUT";
        r.orderNo = orderNo;
        r.qty = qty;
        r.barcode = barcode;
        return r;
    }

    // ==================== Getters / Setters ====================

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
}
