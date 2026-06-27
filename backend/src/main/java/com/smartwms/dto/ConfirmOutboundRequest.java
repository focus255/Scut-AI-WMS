package com.smartwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 确认出库请求 DTO，支持按明细行传入实际出库二维码与数量。
 */
public class ConfirmOutboundRequest {

    @NotEmpty(message = "出库确认明细不能为空")
    @Valid
    private List<ConfirmDetailItem> details;

    public List<ConfirmDetailItem> getDetails() { return details; }
    public void setDetails(List<ConfirmDetailItem> details) { this.details = details; }

    /**
     * 确认出库的明细行。
     */
    public static class ConfirmDetailItem {
        @NotNull(message = "出库明细 ID 不能为空")
        private Long detailId;

        @NotNull(message = "实际出库数量不能为空")
        @Min(value = 1, message = "实际出库数量必须大于 0")
        private Integer actualQty;

        /** 出库二维码列表（工作台模式可不传，扫码模式必传） */
        private List<String> barcodes;

        public Long getDetailId() { return detailId; }
        public void setDetailId(Long detailId) { this.detailId = detailId; }

        public Integer getActualQty() { return actualQty; }
        public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }

        public List<String> getBarcodes() { return barcodes; }
        public void setBarcodes(List<String> barcodes) { this.barcodes = barcodes; }
    }
}
