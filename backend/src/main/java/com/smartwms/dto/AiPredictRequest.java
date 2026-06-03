/**
 * AI 预测触发请求 DTO。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.dto;

import jakarta.validation.constraints.NotBlank;

public class AiPredictRequest {

    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
}
