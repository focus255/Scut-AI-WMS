/**
 * 库存报表与预警 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 查询动态库存水位报表。
 * @param {object} params { materialCode?, alarmStatus? }
 */
export function getStockReport(params) {
  return request.get('/stock/report', { params })
}
