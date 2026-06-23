<!--
  入库箱单看板组件（一码到底，全程唯一标识）。
  左二维码 + 右信息表，不含流程状态等动态数据。

  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="kanban-card" ref="cardRef">
    <!-- 二维码 -->
    <canvas ref="qrCanvasRef" class="kanban-qr"></canvas>
    <!-- 信息表 -->
    <table class="kanban-table">
      <tr>
        <td class="k-label">物料号</td>
        <td class="k-value">{{ info.materialCode }}</td>
      </tr>
      <tr>
        <td class="k-label">供应商</td>
        <td class="k-value">{{ info.supplierCode }}</td>
      </tr>
      <tr>
        <td class="k-label">器具</td>
        <td class="k-value">{{ displayPackType }}</td>
      </tr>
      <tr>
        <td class="k-label">日期</td>
        <td class="k-value">{{ dateStr }}</td>
      </tr>
      <tr>
        <td class="k-label">数量</td>
        <td class="k-value">{{ info.packCapacity }} 件/箱</td>
      </tr>
      <tr>
        <td class="k-label">看板号</td>
        <td class="k-value kanban-no">{{ barcode }}</td>
      </tr>
    </table>
    <!-- 隐藏画布供PNG导出 -->
    <canvas ref="exportCanvasRef" style="display:none"></canvas>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import QRCodeLib from 'qrcode'
import { getAppliances } from '@/api/appliances'

const props = defineProps({
  barcode: { type: String, required: true },
  packType: { type: String, default: '' },
  createdAt: { type: [String, Array], default: '' },
})

const qrCanvasRef = ref(null)
const exportCanvasRef = ref(null)
const resolvedPackType = ref('')

const info = computed(() => {
  const parts = (props.barcode || '').split('|')
  return {
    materialCode: parts[1] || '—',
    supplierCode: parts[2] || '—',
    packCapacity: parseInt(parts[4]) || 0,
  }
})

/** 显示用的器具类型（优先 prop，其次 API 查找） */
const displayPackType = computed(() => props.packType || resolvedPackType.value || '—')

/** 从条码解析的物料号+供应商查找器具类型 */
async function resolvePackType() {
  if (props.packType) { resolvedPackType.value = props.packType; return }
  const mat = info.value.materialCode
  const sup = info.value.supplierCode
  if (mat === '—' || sup === '—') return
  try {
    const data = await getAppliances({ page: 1, size: 20, keyword: mat })
    const match = (data.records || []).find(a => a.materialCode === mat && a.supplierCode === sup)
    if (match) resolvedPackType.value = match.packType || ''
  } catch { /* */ }
}

const dateStr = computed(() => {
  const v = props.createdAt
  if (!v) return new Date().toISOString().substring(0, 10)
  if (typeof v === 'string') return v.substring(0, 10)
  if (Array.isArray(v) && v.length >= 3) {
    return `${v[0]}-${String(v[1]).padStart(2, '0')}-${String(v[2]).padStart(2, '0')}`
  }
  return new Date().toISOString().substring(0, 10)
})

/** 渲染显示屏上的二维码 */
async function renderDisplayQr() {
  await nextTick()
  const canvas = qrCanvasRef.value
  if (!canvas || !props.barcode) return
  const size = 108
  canvas.width = size
  canvas.height = size
  try {
    await QRCodeLib.toCanvas(canvas, props.barcode, {
      width: size, margin: 1,
      color: { dark: '#000', light: '#fff' },
    })
  } catch { /* ignore */ }
}

/** 渲染 PNG 导出画布（左二维码 + 右表格） */
async function renderExportCanvas() {
  await nextTick()
  const canvas = exportCanvasRef.value
  if (!canvas) return
  const W = 420, H = 210
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, W, H)

  // 外框
  ctx.strokeStyle = '#333'; ctx.lineWidth = 2
  ctx.strokeRect(3, 3, W - 6, H - 6)

  // — 左侧二维码 —
  const qrSize = 100, qrX = 18, qrY = 18
  ctx.fillStyle = '#f8f9fa'
  ctx.fillRect(qrX - 2, qrY - 2, qrSize + 4, qrSize + 4)
  ctx.strokeStyle = '#dee2e6'; ctx.lineWidth = 0.8
  ctx.strokeRect(qrX - 2, qrY - 2, qrSize + 4, qrSize + 4)
  try {
    const qrCanvas = document.createElement('canvas')
    await QRCodeLib.toCanvas(qrCanvas, props.barcode, {
      width: qrSize, margin: 1,
      color: { dark: '#000', light: '#fff' },
    })
    ctx.drawImage(qrCanvas, qrX, qrY)
  } catch { /* ignore */ }

  // 二维码下方文字
  ctx.fillStyle = '#888'
  ctx.font = '9px "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('扫一扫查看', qrX + qrSize / 2, qrY + qrSize + 16)

  // — 右侧信息表 —
  const tx = 140, ty = 24, lh = 28
  const rows = [
    ['物料号', info.value.materialCode],
    ['供应商', info.value.supplierCode],
    ['器具', displayPackType.value],
    ['日期', dateStr.value],
    ['数量', info.value.packCapacity + ' 件/箱'],
    ['看板号', props.barcode],
  ]
  rows.forEach((r, i) => {
    const y = ty + i * lh
    ctx.fillStyle = '#888'
    ctx.font = '11px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'left'
    ctx.fillText(r[0], tx, y + 12)
    ctx.fillStyle = '#222'
    ctx.font = 'bold 12px "Microsoft YaHei", sans-serif'
    ctx.fillText(r[1], tx + 62, y + 12)
    // 分隔线
    if (i > 0) {
      ctx.strokeStyle = '#eee'; ctx.lineWidth = 0.4
      ctx.beginPath(); ctx.moveTo(tx, y - 2); ctx.lineTo(W - 18, y - 2); ctx.stroke()
    }
  })
}

function getCanvas() { return exportCanvasRef.value }

onMounted(async () => { await resolvePackType(); renderDisplayQr(); renderExportCanvas() })
watch(() => [props.barcode, props.packType, props.createdAt], async () => { await resolvePackType(); renderDisplayQr(); renderExportCanvas() })

defineExpose({ getCanvas })
</script>

<style scoped>
.kanban-card {
  background: #fff;
  border: 2px solid #2c3e50;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  width: 100%;
  max-width: 420px;
}
.kanban-qr {
  width: 100px;
  height: 100px;
  flex-shrink: 0;
  border: 1px solid #eee;
  border-radius: 3px;
  padding: 3px;
  background: #fff;
}
.kanban-table {
  flex: 1;
  min-width: 0;
  border-collapse: collapse;
  font-size: 12px;
}
.kanban-table td {
  padding: 2px 6px;
  vertical-align: middle;
}
.k-label {
  color: #888;
  font-size: 11px;
  white-space: nowrap;
  width: 48px;
}
.k-value {
  color: #222;
  font-weight: 600;
  font-size: 12px;
}
.kanban-no {
  font-size: 10px;
  font-family: "Courier New", monospace;
  word-break: break-all;
}
</style>
