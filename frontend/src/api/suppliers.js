/**
 * 供应商管理 API。
 *
 * @author Claude
 * @date 2026-06-10
 */
import request from './request'

/**
 * 分页查询供应商列表。
 */
export function getSuppliers(params) {
  return request.get('/suppliers', { params })
}

/**
 * 查询供应商详情。
 */
export function getSupplierById(id) {
  return request.get(`/suppliers/${id}`)
}

/**
 * 新增供应商。
 */
export function createSupplier(data) {
  return request.post('/suppliers', data)
}

/**
 * 更新供应商。
 */
export function updateSupplier(id, data) {
  return request.put(`/suppliers/${id}`, data)
}

/**
 * 删除供应商。
 */
export function deleteSupplier(id) {
  return request.delete(`/suppliers/${id}`)
}
