/**
 * 新建入库单请求 DTO。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class InboundOrderRequest {

    @NotBlank(message = "供应商代码不能为空")
    private String supplierCode;

    @NotEmpty(message = "入库明细不能为空")
    private List<InboundDetailItem> details;

    // ==================== Getters / Setters ====================

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public List<InboundDetailItem> getDetails() { return details; }
    public void setDetails(List<InboundDetailItem> details) { this.details = details; }

    /**
     * 入库单明细项。
     */
    public static class InboundDetailItem {
        private String materialCode;
        private Integer packCapacity;
        private Integer planQty;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getPackCapacity() { return packCapacity; }
        public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

        public Integer getPlanQty() { return planQty; }
        public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    }
}
