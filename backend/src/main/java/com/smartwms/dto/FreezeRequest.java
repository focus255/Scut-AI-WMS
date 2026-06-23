/**
 * 封存解封请求 DTO。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class FreezeRequest {

    @NotEmpty(message = "条码列表不能为空")
    private List<String> barcodes;

    @NotBlank(message = "封存类型不能为空")
    private String freezeType;

    @NotBlank(message = "封存原因不能为空")
    private String reason;

    public List<String> getBarcodes() { return barcodes; }
    public void setBarcodes(List<String> barcodes) { this.barcodes = barcodes; }

    public String getFreezeType() { return freezeType; }
    public void setFreezeType(String freezeType) { this.freezeType = freezeType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
