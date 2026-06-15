/**
 * 入库历史 API。
 *
 * @author Claude
 * @date 2026-06-15
 */
import request from './request'

/**
 * 查询入库历史（支持日期范围、状态、关键字筛选，含汇总统计）。
 * @param {Object} params { page, size, startDate?, endDate?, status?, keyword? }
 * @returns {{ records, total, summary: { totalBatches, totalQty, dailyTrend } }}
 */
export function getInboundHistory(params) {
  return request.get('/inbound/history', { params })
}

/**
 * 批量导入入库单。
 * @param {Array} requests 入库单请求列表
 */
export function batchCreateInbound(requests) {
  return request.post('/inbound/batch', requests)
}
