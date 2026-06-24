<!--
  手机端全屏扫码页 — 持续扫码 + 结果内联展示。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="scanner-page">
    <!-- 内联摄像头 -->
    <div class="camera-zone">
      <BarcodeScanner ref="scannerRef" :inline="true" @scanned="onScanned" />
    </div>

    <!-- 结果列表（最新在上） -->
    <div class="results-area" v-if="scanResults.length > 0">
      <div v-for="(r, i) in scanResults" :key="i" class="result-item"
        :class="r.ok ? 'result-ok' : 'result-fail'">
        <div class="result-top">
          <span class="result-badge">{{ r.ok ? '✓' : '✗' }}</span>
          <span class="result-mat">{{ r.materialCode }}</span>
          <span v-if="r.qty" class="result-qty">{{ r.qty }}件</span>
        </div>
        <div class="result-bc">{{ r.barcode }}</div>
        <div v-if="r.error" class="result-err">{{ r.error }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { scanInbound } from '@/api/inbound'
import { sealBarcodes, unsealBarcode } from '@/api/freeze'
import request from '@/api/request'
import BarcodeScanner from '@/components/BarcodeScanner.vue'

const route = useRoute()
const mode = computed(() => route.params.mode)
const scanResults = ref([])

function parseMaterial(code) {
  if (!code || !code.startsWith('WMS|')) return '—'
  return code.split('|')[1] || '—'
}
function parseQty(code) {
  if (!code || !code.startsWith('WMS|')) return 0
  return parseInt(code.split('|')[4]) || 0
}

async function onScanned(code) {
  const entry = { barcode: code, ok: false, materialCode: parseMaterial(code), qty: parseQty(code), error: '' }
  try {
    const isInboundBc = code.startsWith('WMS|')
    const isOutboundBc = code.startsWith('OUT|')

    switch (mode.value) {
      case 'inbound':
        if (isOutboundBc) throw new Error('请扫描入库二维码')
        const ib = await scanInbound({ barcode: code })
        entry.materialCode = ib.materialCode; entry.qty = ib.qty
        break
      case 'outbound':
        if (!isInboundBc) throw new Error('请扫描WMS入库二维码')
        // 出库扫码：直接调后端 scanOutbound（不接受 unifiedScan 的自动路由）
        const ob = await request.post('/outbound/scan/wms', { barcode: code })
        entry.materialCode = ob.materialCode; entry.qty = ob.qty
        break
      case 'seal':
        if (isOutboundBc) throw new Error('出库标签不可封存')
        await sealBarcodes({ barcodes: [code], freezeType: '扫码', reason: '扫码封存' })
        break
      case 'unseal':
        if (isOutboundBc) throw new Error('出库标签不可解封')
        await unsealBarcode(code)
        break
    }
    entry.ok = true
  } catch (err) {
    entry.error = err.message || '操作失败'
  }
  scanResults.value.unshift(entry)
}
</script>

<style scoped>
.scanner-page { display: flex; flex-direction: column; height: 100%; max-width: 420px; margin: 0 auto; }

.camera-zone {
  flex-shrink: 0; background: #000; overflow: hidden;
  width: 100%;
}

.results-area { flex: 1; overflow-y: auto; padding: 8px 0; }
.result-item { margin: 6px 0; padding: 10px 12px; border-radius: 8px; }
.result-ok { background: #f0f9eb; border: 1px solid #e1f3d8; }
.result-fail { background: #fef0f0; border: 1px solid #fde2e2; }
.result-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.result-badge { font-weight: 700; font-size: 16px; }
.result-ok .result-badge { color: #67c23a; }
.result-fail .result-badge { color: #f56c6c; }
.result-mat { font-size: 14px; font-weight: 600; color: #303133; }
.result-qty { font-size: 13px; color: #606266; }
.result-bc { font-size: 11px; color: #999; font-family: monospace; word-break: break-all; }
.result-err { font-size: 12px; color: #f56c6c; margin-top: 2px; }
</style>
