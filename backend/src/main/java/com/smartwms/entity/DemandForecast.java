package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

/** 需求预测与波动监控实体 */
@TableName("demand_forecasts")
public class DemandForecast {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;
    private String weeklyHistory;       // 出库历史 JSON
    private String inboundHistory;      // 入库历史 JSON
    @TableField("week_1") private Integer week1;
    @TableField("week_2") private Integer week2;
    @TableField("week_3") private Integer week3;
    @TableField("week_4") private Integer week4;
    @TableField("in_week_1") private Integer inWeek1;
    @TableField("in_week_2") private Integer inWeek2;
    @TableField("in_week_3") private Integer inWeek3;
    @TableField("in_week_4") private Integer inWeek4;
    private String trend;
    private String volatility;
    private Boolean anomalyFlag;
    private String analysis;
    private String model;
    private LocalDateTime generatedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // === Getters/Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String c) { this.materialCode = c; }
    public String getWeeklyHistory() { return weeklyHistory; }
    public void setWeeklyHistory(String h) { this.weeklyHistory = h; }
    public String getInboundHistory() { return inboundHistory; }
    public void setInboundHistory(String h) { this.inboundHistory = h; }
    public Integer getWeek1() { return week1; }
    public void setWeek1(Integer v) { this.week1 = v; }
    public Integer getWeek2() { return week2; }
    public void setWeek2(Integer v) { this.week2 = v; }
    public Integer getWeek3() { return week3; }
    public void setWeek3(Integer v) { this.week3 = v; }
    public Integer getWeek4() { return week4; }
    public void setWeek4(Integer v) { this.week4 = v; }
    public Integer getInWeek1() { return inWeek1; }
    public void setInWeek1(Integer v) { this.inWeek1 = v; }
    public Integer getInWeek2() { return inWeek2; }
    public void setInWeek2(Integer v) { this.inWeek2 = v; }
    public Integer getInWeek3() { return inWeek3; }
    public void setInWeek3(Integer v) { this.inWeek3 = v; }
    public Integer getInWeek4() { return inWeek4; }
    public void setInWeek4(Integer v) { this.inWeek4 = v; }
    public String getTrend() { return trend; }
    public void setTrend(String t) { this.trend = t; }
    public String getVolatility() { return volatility; }
    public void setVolatility(String v) { this.volatility = v; }
    public Boolean getAnomalyFlag() { return anomalyFlag; }
    public void setAnomalyFlag(Boolean f) { this.anomalyFlag = f; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String a) { this.analysis = a; }
    public String getModel() { return model; }
    public void setModel(String m) { this.model = m; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime t) { this.generatedAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
