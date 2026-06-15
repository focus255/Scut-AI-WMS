/**
 * 通用数据导出工具。
 *
 * @author Claude
 * @date 2026-06-15
 */

/**
 * 导出数据为 CSV 文件并触发下载。
 * @param {Array<Object>} columns 列定义 [{ key: 'fieldName', label: '列标题' }]
 * @param {Array<Object>} data    数据行数组
 * @param {string}         filename 文件名（不含扩展名）
 */
export function exportCSV(columns, data, filename = 'export') {
  if (!data || !data.length) return

  // BOM 头确保 Excel 正确识别 UTF-8 中文
  const BOM = '﻿'
  const header = columns.map(c => escapeCSV(c.label)).join(',')
  const rows = data.map(row =>
    columns.map(c => escapeCSV(row[c.key] ?? '')).join(',')
  )
  const csv = BOM + [header, ...rows].join('\n')

  downloadBlob(csv, `${filename}.csv`, 'text/csv;charset=utf-8')
}

/**
 * 转义 CSV 字段（含逗号、引号或换行时用双引号包裹）。
 */
function escapeCSV(value) {
  const str = String(value ?? '')
  if (str.includes(',') || str.includes('"') || str.includes('\n') || str.includes('\r')) {
    return '"' + str.replace(/"/g, '""') + '"'
  }
  return str
}

/**
 * 触发浏览器下载 Blob。
 */
function downloadBlob(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
