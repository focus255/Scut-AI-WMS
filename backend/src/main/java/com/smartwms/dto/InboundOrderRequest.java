/**
 * 新建入库单请求 DTO。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class InboundOrderRequest {

    @NotBlank(message = "供应商代码不能为空")
    private String supplierCode;

    @NotEmpty(message = "入库明细不能为空")
    @Valid
    private List<InboundDetailItem> details;

    // ==================== Getters / Setters ====================

    public String getSupplierCode() { return supplierCode; }
    public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

    public List<InboundDetailItem> getDetails() { return details; }
    public void setDetails(List<InboundDetailItem> details) { this.details = details; }

    /**
     * 入库单明细项（整箱入库，单箱容量由器具配置决定）。
     */
    public static class InboundDetailItem {
        @NotBlank(message = "物料号不能为空")
        private String materialCode;

        @NotNull(message = "入库箱数不能为空")
        @Min(value = 1, message = "入库箱数必须大于 0")
        private Integer boxCount;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
    }
}
