/**
 * 出库单 API。
 *
 * @author Focus
 * @date 2026-06-15
 */
import request from './request'

/**
 * 创建出库单。
 * @param {Object} data { details: [{ materialCode, packCapacity, planQty }] }
 */
export function createOutbound(data) {
  return request.post('/outbound/orders', data)
}

/**
 * 分页查询出库单列表。
 */
export function getOutboundOrders(params) {
  return request.get('/outbound/orders', { params })
}

/**
 * 查询出库单详情（含明细行和出库流水）。
 * @param {number} id 出库单主键 ID
 */
export function getOutboundDetail(id) {
  return request.get(`/outbound/orders/${id}`)
}

/**
 * 确认出库：按明细行传入实际出库二维码与数量。
 * @param {number} id 出库单主键 ID
 * @param {Object} data { details: [{ detailId, actualQty, barcodes }] }
 */
export function confirmOutbound(id, data) {
  return request.put(`/outbound/orders/${id}/confirm`, data)
}

/**
 * 分页查询出库批次流水。
 * @param {Object} params { page, size, orderNo?, materialCode? }
 */
export function getOutboundHistories(params) {
  return request.get('/outbound/histories', { params })
}

/**
 * 修改出库单（仅未出库/部分出库状态）。
 * @param {number} id 出库单 ID
 * @param {Object} data { details }
 */
export function updateOutbound(id, data) {
  return request.put(`/outbound/orders/${id}`, data)
}

/**
 * 删除出库单（仅未出库状态）。
 * @param {number} id 出库单 ID
 */
export function deleteOutbound(id) {
  return request.delete(`/outbound/orders/${id}`)
}

/**
 * 统一扫码：自动判定入库/出库二维码并执行对应操作。
 * @param {Object} data { barcode }
 */
export function unifiedScan(data) {
  return request.post('/outbound/scan', data)
}

/**
 * 出库单摘要统计（全局，不受分页影响）。
 * @param {Object} params { status?, orderNo?, startDate?, endDate? }
 */
export function getOutboundSummary(params) {
  return request.get('/outbound/orders/summary', { params })
}
