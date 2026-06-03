/**
 * 物料基础信息 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 分页查询物料列表。
 */
export function getMaterials(params) {
  return request.get('/materials', { params })
}

/**
 * 查询物料详情。
 */
export function getMaterialById(id) {
  return request.get(`/materials/${id}`)
}

/**
 * 新增物料。
 */
export function createMaterial(data) {
  return request.post('/materials', data)
}

/**
 * 更新物料。
 */
export function updateMaterial(id, data) {
  return request.put(`/materials/${id}`, data)
}

/**
 * 删除物料。
 */
export function deleteMaterial(id) {
  return request.delete(`/materials/${id}`)
}
