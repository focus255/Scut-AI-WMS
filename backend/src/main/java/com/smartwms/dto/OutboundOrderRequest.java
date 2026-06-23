/**
 * 创建出库单请求（整箱出库，单箱容量由器具配置决定）。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OutboundOrderRequest {

    @NotEmpty(message = "出库明细不能为空")
    @Valid
    private List<OutboundDetailItem> details;

    public List<OutboundDetailItem> getDetails() { return details; }
    public void setDetails(List<OutboundDetailItem> details) { this.details = details; }

    /**
     * 出库单明细项（整箱出库，不拆零）。
     */
    public static class OutboundDetailItem {
        @NotBlank(message = "物料号不能为空")
        private String materialCode;

        @NotNull(message = "出库箱数不能为空")
        @Min(value = 1, message = "出库箱数必须大于 0")
        private Integer boxCount;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getBoxCount() { return boxCount; }
        public void setBoxCount(Integer boxCount) { this.boxCount = boxCount; }
    }
}
