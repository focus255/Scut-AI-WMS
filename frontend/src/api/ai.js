/**
 * AI 预测与报告 API。
 *
 * @author Focus
 * @date 2026-06-03
 */
import request from './request'

/**
 * 触发物料 AI 风险预测（异步）。
 * @param {string} materialCode 物料编码
 */
export function triggerPredict(materialCode) {
  return request.post('/ai/predict', { materialCode })
}

/**
 * 查询指定物料的最新 AI 分析报告。
 * @param {string} materialCode 物料编码
 */
export function getLatestReport(materialCode) {
  return request.get('/ai/reports/latest', { params: { materialCode } })
}
