/**
 * 扫码入库请求 DTO。
 *
 * @author Focus
 * @date 2026-06-10
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;

public class ScanInboundRequest {

    /** 扫描或手动输入的看板号 */
    @NotBlank(message = "二维码不能为空")
    private String barcode;

    /** 实际入库数量（可选，默认按箱容量入库） */
    private Integer actualQty;

    // ==================== Getters / Setters ====================

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
}
