/**
 * 库存追溯查询响应 VO，包含二维码生命周期轨迹列表。
 *
 * @author Focus
 * @date 2026-06-10
 */
package com.smartwms.dto;

import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.OutboundHistory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryTraceVO {

    private List<TraceItem> items;
    private int totalCount;
    /** 全局状态统计（不受分页影响，统计当前筛选条件下的全部条码） */
    private Map<String, Integer> statusStats;

    public static InventoryTraceVO of(List<TraceItem> items) {
        return of(items, items != null ? items.size() : 0, null);
    }
    public static InventoryTraceVO of(List<TraceItem> items, long totalCount) {
        return of(items, totalCount, null);
    }
    public static InventoryTraceVO of(List<TraceItem> items, long totalCount, Map<String, Integer> statusStats) {
        InventoryTraceVO vo = new InventoryTraceVO();
        vo.setItems(items != null ? items : new ArrayList<>());
        vo.setTotalCount((int) totalCount);
        vo.setStatusStats(statusStats);
        return vo;
    }

    // ==================== Getters / Setters ====================

    public List<TraceItem> getItems() { return items; }
    public void setItems(List<TraceItem> items) { this.items = items; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public Map<String, Integer> getStatusStats() { return statusStats; }
    public void setStatusStats(Map<String, Integer> statusStats) { this.statusStats = statusStats; }

    /**
     * 单条追溯记录，由二维码与入库明细联查组装。
     */
    public static class TraceItem {

        private String barcode;
        private String materialCode;
        private String supplierCode;
        private String orderNo;
        private String status;
        private Integer packCapacity;
        private Integer planQty;
        private Integer actualQty;
        private Integer remainingQty;
        private String inboundCreatedAt;
        private String outboundOrderNo;
        private String outboundAt;
        private String barcodeCreatedAt;
        private String barcodeUpdatedAt;

        /**
         * 从二维码和入库明细组装追溯条目。
         */
        public static TraceItem from(Barcode bc, InboundDetail detail, OutboundHistory outboundHistory) {
            TraceItem item = new TraceItem();
            item.setBarcode(bc.getBarcode());
            item.setMaterialCode(bc.getMaterialCode());
            item.setSupplierCode(bc.getSupplierCode());
            item.setStatus(bc.getStatus());
            item.setRemainingQty(bc.getRemainingQty());
            item.setBarcodeCreatedAt(bc.getCreatedAt() != null
                    ? bc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            item.setBarcodeUpdatedAt(bc.getUpdatedAt() != null
                    ? bc.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            if (detail != null) {
                item.setOrderNo(detail.getOrderNo());
                item.setPackCapacity(detail.getPackCapacity());
                item.setPlanQty(detail.getPlanQty());
                item.setActualQty(detail.getActualQty());
                item.setInboundCreatedAt(detail.getCreatedAt() != null
                        ? detail.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            }
            if (outboundHistory != null) {
                item.setOutboundOrderNo(outboundHistory.getOutboundOrderNo());
                item.setOutboundAt(outboundHistory.getCreatedAt() != null
                        ? outboundHistory.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
            }
            return item;
        }

        // ==================== Getters / Setters ====================

        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }

        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

        public String getSupplierCode() { return supplierCode; }
        public void setSupplierCode(String supplierCode) { this.supplierCode = supplierCode; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Integer getPackCapacity() { return packCapacity; }
        public void setPackCapacity(Integer packCapacity) { this.packCapacity = packCapacity; }

        public Integer getPlanQty() { return planQty; }
        public void setPlanQty(Integer planQty) { this.planQty = planQty; }

        public Integer getActualQty() { return actualQty; }
        public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }

        public Integer getRemainingQty() { return remainingQty; }
        public void setRemainingQty(Integer remainingQty) { this.remainingQty = remainingQty; }

        public String getInboundCreatedAt() { return inboundCreatedAt; }
        public void setInboundCreatedAt(String inboundCreatedAt) { this.inboundCreatedAt = inboundCreatedAt; }

        public String getOutboundOrderNo() { return outboundOrderNo; }
        public void setOutboundOrderNo(String outboundOrderNo) { this.outboundOrderNo = outboundOrderNo; }

        public String getOutboundAt() { return outboundAt; }
        public void setOutboundAt(String outboundAt) { this.outboundAt = outboundAt; }

        public String getBarcodeCreatedAt() { return barcodeCreatedAt; }
        public void setBarcodeCreatedAt(String barcodeCreatedAt) { this.barcodeCreatedAt = barcodeCreatedAt; }

        public String getBarcodeUpdatedAt() { return barcodeUpdatedAt; }
        public void setBarcodeUpdatedAt(String barcodeUpdatedAt) { this.barcodeUpdatedAt = barcodeUpdatedAt; }
    }
}
