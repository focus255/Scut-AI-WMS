<!--
  入库箱单标签（看板标签）组件。
  使用 Canvas 渲染完整标签，包含二维码和文字信息，宽高比约 1.4。
  可直接下载为 PNG，也可用于打印。

  @author Focus
  @date 2026-06-13
-->
<template>
  <div class="box-label-wrapper" :style="{ maxWidth: displayWidth + 'px' }">
    <canvas ref="canvasRef" class="box-label-canvas"></canvas>
    <!-- 隐藏的下载用画布，保持原始分辨率 -->
    <canvas ref="exportCanvasRef" style="display:none"></canvas>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import QRCodeLib from 'qrcode'

const props = defineProps({
  /** 完整条码字符串，格式: WMS|物料|供应商|计划数|箱容量|实收数|箱号 */
  barcode: { type: String, required: true },
  /** 条码状态：待入库 / 在库 / 已出库 */
  status: { type: String, default: '待入库' },
  /** 关联入库单号 */
  orderNo: { type: String, default: '' },
  /** 创建时间（兼容 ISO 字符串和 Jackson 数组格式） */
  createdAt: { type: [String, Array], default: '' },
})

const canvasRef = ref(null)
const exportCanvasRef = ref(null)

// 画布实际尺寸（宽高比 1.4）
const CANVAS_WIDTH = 420
const CANVAS_HEIGHT = 300
// CSS 显示宽度（可被父级覆盖）
const displayWidth = 300

/**
 * 解析条码字符串，提取各字段。
 * 格式: WMS|<materialCode>|<supplierCode>|<planQty>|<packCapacity>|<actualQty>|<boxSeq>
 */
function parseBarcode(str) {
  const parts = (str || '').split('|')
  return {
    materialCode: parts[1] || '—',
    supplierCode: parts[2] || '—',
    planQty: parseInt(parts[3]) || 0,
    packCapacity: parseInt(parts[4]) || 0,
    actualQty: parseInt(parts[5]) || 0,
    boxSeq: parseInt(parts[6]) || 1,
  }
}

/**
 * 格式化日期值，兼容多种序列化格式。
 * - ISO 字符串: "2026-06-03T10:30:00" → "2026-06-03"
 * - Jackson 数组: [2026, 6, 3, 10, 30, 0] → "2026-06-03"
 * - 空值: 返回当前日期
 */
function formatDate(value) {
  if (!value) return new Date().toISOString().substring(0, 10)
  if (typeof value === 'string') return value.substring(0, 10)
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d] = value
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
  }
  return new Date().toISOString().substring(0, 10)
}

/**
 * 在画布上绘制完整标签。
 * @param {HTMLCanvasElement} canvas 目标画布
 */
async function drawLabel(canvas) {
  if (!canvas || !props.barcode) return

  const ctx = canvas.getContext('2d')
  canvas.width = CANVAS_WIDTH
  canvas.height = CANVAS_HEIGHT

  const info = parseBarcode(props.barcode)
  // 总箱数 = 计划数量 ÷ 单箱容量（向上取整）
  const totalBoxes = info.packCapacity > 0
    ? Math.ceil(info.planQty / info.packCapacity)
    : 1

  // ==================== 背景 ====================
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT)

  // ==================== 外边框 ====================
  ctx.strokeStyle = '#2c3e50'
  ctx.lineWidth = 2.5
  ctx.strokeRect(2, 2, CANVAS_WIDTH - 4, CANVAS_HEIGHT - 4)

  // 内边框（装饰线）
  ctx.strokeStyle = '#bdc3c7'
  ctx.lineWidth = 0.8
  ctx.strokeRect(8, 8, CANVAS_WIDTH - 16, CANVAS_HEIGHT - 16)

  // ==================== 标题栏 ====================
  const titleY = 38
  ctx.fillStyle = '#2c3e50'
  ctx.font = 'bold 15px "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText('智库 WMS — 入库箱单标签', CANVAS_WIDTH / 2, titleY)

  // 标题下方分隔线
  ctx.strokeStyle = '#2c3e50'
  ctx.lineWidth = 1.2
  ctx.beginPath()
  ctx.moveTo(24, titleY + 8)
  ctx.lineTo(CANVAS_WIDTH - 24, titleY + 8)
  ctx.stroke()

  // ==================== 左侧：二维码 ====================
  const qrSize = 118
  const qrX = 24
  const qrY = 62

  // 二维码背景框
  ctx.fillStyle = '#f8f9fa'
  ctx.fillRect(qrX - 4, qrY - 4, qrSize + 8, qrSize + 8)
  ctx.strokeStyle = '#dee2e6'
  ctx.lineWidth = 1
  ctx.strokeRect(qrX - 4, qrY - 4, qrSize + 8, qrSize + 8)

  // 渲染二维码到离屏 canvas 再绘制到主画布
  try {
    const qrCanvas = document.createElement('canvas')
    await QRCodeLib.toCanvas(qrCanvas, props.barcode, {
      width: qrSize,
      height: qrSize,
      margin: 1,
      color: { dark: '#000000', light: '#ffffff' },
    })
    ctx.drawImage(qrCanvas, qrX, qrY, qrSize, qrSize)
  } catch {
    // 二维码渲染失败时显示占位文字
    ctx.fillStyle = '#e74c3c'
    ctx.font = '11px "Microsoft YaHei", sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('二维码生成失败', qrX + qrSize / 2, qrY + qrSize / 2)
  }

  // ==================== 右侧：文字信息 ====================
  const textX = 162
  let textY = 72
  const lineHeight = 22
  const labelColor = '#7f8c8d'
  const valueColor = '#2c3e50'

  /**
   * 绘制一行标签文字。
   * @param {string} label 标签名
   * @param {string} value 值
   */
  function drawInfoLine(label, value) {
    ctx.textAlign = 'left'
    ctx.fillStyle = labelColor
    ctx.font = '12px "Microsoft YaHei", "PingFang SC", sans-serif'
    ctx.fillText(label, textX, textY)
    ctx.fillStyle = valueColor
    ctx.font = 'bold 12px "Microsoft YaHei", "PingFang SC", sans-serif'
    ctx.fillText(value, textX + 68, textY)
    textY += lineHeight
  }

  drawInfoLine('物料编码：', info.materialCode)
  drawInfoLine('供应商：', info.supplierCode)
  drawInfoLine('入库单号：', props.orderNo || '—')
  drawInfoLine('计划数量：', String(info.planQty))
  drawInfoLine('单箱容量：', String(info.packCapacity))
  drawInfoLine('箱　　号：', `${info.boxSeq} / ${totalBoxes}`)
  drawInfoLine('状　　态：', props.status || '—')

  // 日期行（使用 createdAt 或当前日期）
  // createdAt 可能为 ISO 字符串 "2026-06-03T10:30:00"、数组 [2026,6,3,10,30,0] 或空
  const dateStr = formatDate(props.createdAt)
  drawInfoLine('日　　期：', dateStr)

  // ==================== 底部条码字符串 ====================
  const bottomY = CANVAS_HEIGHT - 26
  ctx.strokeStyle = '#dee2e6'
  ctx.lineWidth = 0.6
  ctx.beginPath()
  ctx.moveTo(24, bottomY - 10)
  ctx.lineTo(CANVAS_WIDTH - 24, bottomY - 10)
  ctx.stroke()

  ctx.fillStyle = '#95a5a6'
  ctx.font = '9px "Courier New", "Liberation Mono", monospace'
  ctx.textAlign = 'center'
  // 截断过长条码，保留完整信息
  const displayBarcode = props.barcode.length > 58
    ? props.barcode.substring(0, 55) + '...'
    : props.barcode
  ctx.fillText(displayBarcode, CANVAS_WIDTH / 2, bottomY + 4)

  // 底部小字
  ctx.fillStyle = '#bdc3c7'
  ctx.font = '8px "Microsoft YaHei", sans-serif'
  ctx.fillText('智库 WMS 仓储管理系统', CANVAS_WIDTH / 2, bottomY + 18)
}

/**
 * 绘制到显示画布和导出画布。
 */
async function render() {
  await nextTick()
  if (canvasRef.value) {
    await drawLabel(canvasRef.value)
  }
  // 同步更新导出画布（供父组件通过 getCanvas 获取原始分辨率画布）
  if (exportCanvasRef.value) {
    await drawLabel(exportCanvasRef.value)
  }
}

/**
 * 获取可用于导出的 canvas 元素（原始分辨率）。
 * @returns {HTMLCanvasElement|null}
 */
function getCanvas() {
  return exportCanvasRef.value || canvasRef.value
}

onMounted(() => { render() })
watch(() => [props.barcode, props.status, props.orderNo, props.createdAt], () => { render() })

defineExpose({ getCanvas })
</script>

<style scoped>
.box-label-wrapper {
  display: inline-block;
  line-height: 0;
}
.box-label-canvas {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 3px;
  /* 屏幕显示时添加轻微阴影 */
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.08);
}
</style>
