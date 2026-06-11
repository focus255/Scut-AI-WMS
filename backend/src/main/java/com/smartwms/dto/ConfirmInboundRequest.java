/**
 * 确认入库请求 DTO，支持按明细行传入实际入库数量。
 *
 * @author Claude
 * @date 2026-06-10
 */
package com.smartwms.dto;

import java.util.List;

public class ConfirmInboundRequest {

    private List<ConfirmDetailItem> details;

    public List<ConfirmDetailItem> getDetails() { return details; }
    public void setDetails(List<ConfirmDetailItem> details) { this.details = details; }

    /**
     * 确认入库的明细行（按 materialCode 匹配后端记录）。
     */
    public static class ConfirmDetailItem {
        private String materialCode;
        private Integer actualQty;

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public Integer getActualQty() { return actualQty; }
        public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    }
}
