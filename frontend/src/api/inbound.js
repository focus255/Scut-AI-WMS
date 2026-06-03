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
 * 手工确认入库。
 */
export function confirmInbound(id) {
  return request.put(`/inbound/orders/${id}/confirm`)
}
