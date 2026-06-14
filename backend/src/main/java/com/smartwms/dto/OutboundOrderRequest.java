package com.smartwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建出库单请求。
 */
public class OutboundOrderRequest {

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<OutboundDetailItem> details;

    public List<OutboundDetailItem> getDetails() { return details; }
    public void setDetails(List<OutboundDetailItem> details) { this.details = details; }

    public static class OutboundDetailItem {
        @NotBlank(message = "物料号不能为空")
        private String materialCode;

        @NotNull(message = "包装容量不能为空")
        @Min(value = 1, message = "包装容量必须大于 0")
        private Integer packCapacity;

        @NotNull(message = "计划出库数量不能为空")
        @Min(value = 1, message = "计划出库数量必须大于 0")
        private Integer planQty;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public Integer getPackCapacity() { return packCapacity; }
        public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }
        public Integer getPlanQty() { return planQty; }
        public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    }
}
