/**
 * 封存解封 API。
 *
 * @author Focus
 * @date 2026-06-23
 */
import request from './request'

/** 封存条码 */
export function sealBarcodes(data) {
  return request.post('/freeze/seal', data)
}

/** 解封条码 */
export function unsealBarcode(barcode) {
  return request.post('/freeze/unseal', null, { params: { barcode } })
}

/** 查询封存记录 */
export function getFreezeList(params) {
  return request.get('/freeze/list', { params })
}
