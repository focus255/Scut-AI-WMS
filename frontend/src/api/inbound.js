/**
 * 入库单 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 创建入库单。
 */
export function createInbound(data) {
  return request.post('/inbound/orders', data)
}

/**
 * 分页查询入库单列表。
 */
export function getInboundOrders(params) {
  return request.get('/inbound/orders', { params })
}

/**
 * 查询入库单详情（含明细行）。
 */
export function getInboundDetail(id) {
  return request.get(`/inbound/orders/${id}`)
}

/**
 * 修改入库单（仅"未入库"状态可修改）。
 * @param {number} id 入库单主键 ID
 * @param {Object} data { supplierCode, details }
 */
export function updateInbound(id, data) {
  return request.put(`/inbound/orders/${id}`, data)
}

/**
 * 手工确认入库（支持按明细行传入实际到货数量）。
 * @param {number} id 入库单主键 ID
 * @param {Array} details [{ materialCode, actualQty }, ...] 可选，不传则按计划数全量入库
 */
export function confirmInbound(id, details) {
  return request.put(`/inbound/orders/${id}/confirm`, details ? { details } : {})
}

/**
 * 扫码入库：按条码号精确核销单箱入库。
 * @param {Object} data { barcode, actualQty? }
 */
export function scanInbound(data) {
  return request.post('/inbound/scan', data)
}

/**
 * 库存追溯：按物料/条码/入库单号查询条码生命周期轨迹。
 * @param {Object} params { materialCode?, barcode?, orderNo? }
 */
export function getInventoryTrace(params) {
  return request.get('/inbound/trace', { params })
}
