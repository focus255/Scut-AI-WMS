/**
 * 出库管理 API。
 *
 * @author Claude
 * @date 2026-06-15
 */
import request from './request'

/**
 * 分页查询出库单列表。
 */
export function getOutboundOrders(params) {
  return request.get('/outbound/orders', { params })
}

/**
 * 查询出库单详情（含明细行与出库流水）。
 */
export function getOutboundDetail(id) {
  return request.get(`/outbound/orders/${id}`)
}

/**
 * 创建出库单。
 * @param {Object} data { details: [{ materialCode, packCapacity, planQty }] }
 */
export function createOutbound(data) {
  return request.post('/outbound/orders', data)
}

/**
 * 确认出库（含条码核销）。
 * @param {number} id 出库单主键 ID
 * @param {Object} data { details: [{ detailId, actualQty, barcodes }] }
 */
export function confirmOutbound(id, data) {
  return request.put(`/outbound/orders/${id}/confirm`, data)
}

/**
 * 分页查询出库历史流水。
 * @param {Object} params { page, size, orderNo?, materialCode? }
 */
export function getOutboundHistories(params) {
  return request.get('/outbound/histories', { params })
}
