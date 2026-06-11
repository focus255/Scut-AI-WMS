/**
 * 器具包装参数 API。
 *
 * @author Claude
 * @date 2026-06-10
 */
import request from './request'

/**
 * 分页查询器具列表。
 */
export function getAppliances(params) {
  return request.get('/appliances', { params })
}

/**
 * 查询器具详情。
 */
export function getApplianceById(id) {
  return request.get(`/appliances/${id}`)
}

/**
 * 新增器具。
 */
export function createAppliance(data) {
  return request.post('/appliances', data)
}

/**
 * 更新器具。
 */
export function updateAppliance(id, data) {
  return request.put(`/appliances/${id}`, data)
}

/**
 * 删除器具。
 */
export function deleteAppliance(id) {
  return request.delete(`/appliances/${id}`)
}
