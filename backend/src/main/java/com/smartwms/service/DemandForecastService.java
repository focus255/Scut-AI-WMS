/**
 * 需求预测与波动监控服务——周聚合 + 异常检测 + LLM 预测。
 *
 * @author Focus
 * @date 2026-06-24
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DemandForecastService {

    private static final Logger log = LoggerFactory.getLogger(DemandForecastService.class);
    private static final int HISTORY_WEEKS = 12;
    private static final int PREDICT_WEEKS = 4;

    private final OutboundHistoryMapper outboundHistoryMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialMapper materialMapper;
    private final DemandForecastMapper forecastMapper;
    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DemandForecastService(OutboundHistoryMapper outboundHistoryMapper,
                                  InboundDetailMapper inboundDetailMapper,
                                  InboundOrderMapper inboundOrderMapper,
                                  InventoryMapper inventoryMapper,
                                  MaterialMapper materialMapper,
                                  DemandForecastMapper forecastMapper,
                                  AIService aiService) {
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
        this.forecastMapper = forecastMapper;
        this.aiService = aiService;
    }

    /** 为单个物料生成需求预测 */
    public DemandForecast generate(String materialCode) {
        // 1. 聚合近12周出库量 + 入库量
        int[] outWeeks = aggregateOutWeekly(materialCode);
        int[] inWeeks = aggregateInWeekly(materialCode);

        // 2. 本地统计（基于出库）
        String trend = detectTrend(outWeeks);
        String volatility = detectVolatility(outWeeks);
        boolean anomaly = detectAnomaly(outWeeks);

        // 3. LLM 预测
        int[] outPred = new int[PREDICT_WEEKS];
        int[] inPred = new int[PREDICT_WEEKS];
        String analysis = "";
        String model = "";

        if (aiService.isConfigured()) {
            JsonNode result = aiService.chat(
                "你是供应链需求预测专家。根据出入库历史数据预测未来4周。只返回JSON。",
                buildPrompt(materialCode, outWeeks, inWeeks));
            if (result != null) {
                outPred[0] = result.path("outWeek1").asInt(outWeeks[11]);
                outPred[1] = result.path("outWeek2").asInt(outWeeks[11]);
                outPred[2] = result.path("outWeek3").asInt(outWeeks[11]);
                outPred[3] = result.path("outWeek4").asInt(outWeeks[11]);
                inPred[0] = result.path("inWeek1").asInt(inWeeks.length > 0 ? inWeeks[inWeeks.length-1] : 0);
                inPred[1] = result.path("inWeek2").asInt(inPred[0]);
                inPred[2] = result.path("inWeek3").asInt(inPred[0]);
                inPred[3] = result.path("inWeek4").asInt(inPred[0]);
                analysis = result.path("analysis").asText("");
                model = aiService.getModelName();
            }
        } else {
            double outAvg = Arrays.stream(outWeeks).average().orElse(0);
            double inAvg = inWeeks.length > 0 ? Arrays.stream(inWeeks).average().orElse(0) : outAvg;
            for (int i = 0; i < PREDICT_WEEKS; i++) { outPred[i] = (int) Math.round(outAvg); inPred[i] = (int) Math.round(inAvg); }
            analysis = buildLocalAnalysis(outWeeks, trend, volatility, anomaly);
            model = "本地统计";
        }

        // 4. 存储
        DemandForecast f = new DemandForecast();
        f.setMaterialCode(materialCode);
        f.setWeeklyHistory(arrayToJson(outWeeks));
        f.setInboundHistory(arrayToJson(inWeeks));
        f.setWeek1(outPred[0]); f.setWeek2(outPred[1]); f.setWeek3(outPred[2]); f.setWeek4(outPred[3]);
        f.setInWeek1(inPred[0]); f.setInWeek2(inPred[1]); f.setInWeek3(inPred[2]); f.setInWeek4(inPred[3]);
        f.setTrend(trend);
        f.setVolatility(volatility);
        f.setAnomalyFlag(anomaly);
        f.setAnalysis(analysis);
        f.setModel(model);
        f.setGeneratedAt(LocalDateTime.now());

        // upsert: 先删旧记录再插入
        DemandForecast old = forecastMapper.selectOne(
            new LambdaQueryWrapper<DemandForecast>()
                .eq(DemandForecast::getMaterialCode, materialCode));
        if (old != null) {
            f.setId(old.getId());
            forecastMapper.updateById(f);
        } else {
            forecastMapper.insert(f);
        }
        return f;
    }

    /** 查询全部预测 */
    public List<DemandForecast> getAll() {
        return forecastMapper.selectList(null);
    }

    // ===== 数据聚合 =====
    private int[] aggregateOutWeekly(String code) {
        int[] w = new int[HISTORY_WEEKS];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < HISTORY_WEEKS; i++) {
            LocalDate ws = today.minusWeeks(HISTORY_WEEKS - i).with(DayOfWeek.MONDAY);
            LocalDate we = ws.plusDays(6);
            List<OutboundHistory> list = outboundHistoryMapper.selectList(
                new LambdaQueryWrapper<OutboundHistory>().eq(OutboundHistory::getMaterialCode, code)
                    .ge(OutboundHistory::getCreatedAt, ws.atStartOfDay())
                    .le(OutboundHistory::getCreatedAt, we.atTime(23, 59, 59)));
            w[i] = list.stream().mapToInt(h -> h.getDeductQty() != null ? h.getDeductQty() : 0).sum();
        }
        return w;
    }

    private int[] aggregateInWeekly(String code) {
        int[] w = new int[HISTORY_WEEKS];
        LocalDate today = LocalDate.now();
        // 查该物料所有入库单（已完成），按创建时间归属到12周
        List<InboundOrder> orders = inboundOrderMapper.selectList(
            new LambdaQueryWrapper<InboundOrder>().eq(InboundOrder::getStatus, "已完成"));
        for (int i = 0; i < HISTORY_WEEKS; i++) {
            LocalDate ws = today.minusWeeks(HISTORY_WEEKS - i).with(DayOfWeek.MONDAY);
            LocalDate we = ws.plusDays(6);
            for (InboundOrder o : orders) {
                if (o.getCreatedAt() == null) continue;
                LocalDate od = o.getCreatedAt().toLocalDate();
                if (!od.isBefore(ws) && !od.isAfter(we)) {
                    // 入库单在此周内，汇总其明细数量
                    List<InboundDetail> details = inboundDetailMapper.selectList(
                        new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, o.getId())
                            .eq(InboundDetail::getMaterialCode, code));
                    w[i] += details.stream().mapToInt(d -> d.getActualQty() != null ? d.getActualQty() : 0).sum();
                }
            }
        }
        // 如果完全没有入库数据，用出库数据的80%作为估算
        if (Arrays.stream(w).sum() == 0) {
            int[] outWeeks = aggregateOutWeekly(code);
            for (int i = 0; i < HISTORY_WEEKS; i++) w[i] = (int) Math.round(outWeeks[i] * 0.8);
        }
        return w;
    }

    // ===== 本地统计 =====
    private String detectTrend(int[] w) {
        int firstHalf = Arrays.stream(w, 0, 6).sum();
        int secondHalf = Arrays.stream(w, 6, 12).sum();
        if (secondHalf > firstHalf * 1.15) return "UP";
        if (secondHalf < firstHalf * 0.85) return "DOWN";
        return "STABLE";
    }

    private String detectVolatility(int[] w) {
        double avg = Arrays.stream(w).average().orElse(0);
        if (avg == 0) return "LOW";
        double variance = Arrays.stream(w).mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0);
        double cv = Math.sqrt(variance) / avg;
        if (cv > 0.8) return "HIGH";
        if (cv > 0.4) return "MEDIUM";
        return "LOW";
    }

    private boolean detectAnomaly(int[] w) {
        double avg = Arrays.stream(w).average().orElse(0);
        double std = Math.sqrt(Arrays.stream(w).mapToDouble(v -> Math.pow(v - avg, 2)).average().orElse(0));
        return Arrays.stream(w).anyMatch(v -> avg > 0 && Math.abs(v - avg) > 2.5 * std);
    }

    // ===== 辅助 =====
    private String arrayToJson(int[] arr) {
        try { return objectMapper.writeValueAsString(arr); } catch (Exception e) { return "[]"; }
    }

    private String buildPrompt(String code, int[] outWeeks, int[] inWeeks) {
        Material m = materialMapper.selectOne(
            new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, code));
        String name = m != null ? m.getMaterialName() : code;
        int outTotal = Arrays.stream(outWeeks).sum();
        int inTotal = Arrays.stream(inWeeks).sum();
        double outAvg = outTotal / (double) HISTORY_WEEKS;
        String t = detectTrend(outWeeks);
        String v = detectVolatility(outWeeks);
        boolean anom = detectAnomaly(outWeeks);
        // 入库是批量补货事件，列出非零周
        StringBuilder inEvents = new StringBuilder();
        for (int i = 0; i < inWeeks.length; i++) {
            if (inWeeks[i] > 0) inEvents.append(String.format("第%d周入库%d件; ", i + 1, inWeeks[i]));
        }
        if (inEvents.isEmpty()) inEvents.append("无批量入库记录");

        // 查当前库存
        Inventory inv = inventoryMapper.selectOne(
            new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, code));
        int stock = inv != null && inv.getStockQty() != null ? inv.getStockQty() : 0;

        return String.format("""
            你是汽车零部件仓库需求分析师。该物料出入库数据如下：

            物料: %s (%s)  当前库存: %d 件
            近12周出库(每周消耗): %s  合计:%d件  周均:%.0f件
            批量入库事件: %s  12周合计入库:%d件
            趋势=%s 波动=%s 异常=%s

            请预测未来4周出库量，并建议是否需要补货及补货量（入库通常是批量事件，
            不是每周发生，无需预测每周入库量，只需给出建议补货总量和时机）。
            返回JSON:
            {"outWeek1":n,"outWeek2":n,"outWeek3":n,"outWeek4":n,
             "inWeek1":0,"inWeek2":0,"inWeek3":0,"inWeek4":0,
             "analysis":"出库趋势分析和补货建议80字"}
            """, code, name, stock,
            Arrays.toString(outWeeks), outTotal, outAvg,
            inEvents.toString(), inTotal, t, v, anom ? "是" : "否");
    }

    private String buildLocalAnalysis(int[] w, String trend, String vol, boolean anomaly) {
        int total = Arrays.stream(w).sum();
        double avg = total / (double) HISTORY_WEEKS;
        String t = trend.equals("UP") ? "上升" : trend.equals("DOWN") ? "下降" : "平稳";
        return String.format("[本地统计] 近12周合计%d件，周均%.0f件，趋势%s，波动%s%s。未来4周预测为周均%.0f件。",
            total, avg, t, vol, anomaly ? "（检测到异常波动）" : "", avg);
    }
}
