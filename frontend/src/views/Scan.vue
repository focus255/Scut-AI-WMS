<!--
  扫码操作页面。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <div class="block-header">
        <span class="block-title">扫码操作</span>
      </div>

      <!-- 模式选择 -->
      <div class="scan-mode-row">
        <div class="scan-mode-item" :class="{ active: scanMode === 'inbound' }"
          @click="switchScanMode('inbound')">
          <el-icon :size="22"><Box /></el-icon>
          <span>入库</span>
        </div>
        <div class="scan-mode-item" :class="{ active: scanMode === 'outbound' }"
          @click="switchScanMode('outbound')">
          <el-icon :size="22"><Sell /></el-icon>
          <span>出库</span>
        </div>
        <div class="scan-mode-item" :class="{ active: scanMode === 'seal' }"
          @click="switchScanMode('seal')">
          <el-icon :size="22"><Lock /></el-icon>
          <span>封存</span>
        </div>
        <div class="scan-mode-item" :class="{ active: scanMode === 'unseal' }"
          @click="switchScanMode('unseal')">
          <el-icon :size="22"><Unlock /></el-icon>
          <span>解封</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="scan-action-row">
        <div class="scan-action-btn" @click="openScanCamera">
          <el-icon :size="28"><Camera /></el-icon>
          <span>扫码</span>
        </div>
        <div class="scan-action-btn" @click="openScanUpload">
          <el-icon :size="28"><Upload /></el-icon>
          <span>上传</span>
        </div>
      </div>

      <!-- 扫码结果 -->
      <div v-if="scanResult" class="scan-result" :class="'scan-' + scanMode">
        <div class="scan-result-header">
          <el-icon :size="18"><CircleCheckFilled /></el-icon>
          <span>{{ scanModeLabel }}成功</span>
        </div>
        <div class="qr-row"><span class="qr-label">物料号</span><span class="qr-value">{{ scanResult.materialCode }}</span></div>
        <div class="qr-row" v-if="scanResult.orderNo"><span class="qr-label">{{ scanMode === 'outbound' ? '出库单号' : '入库单号' }}</span><span class="qr-value">{{ scanResult.orderNo }}</span></div>
        <div class="qr-row" v-if="scanResult.qty"><span class="qr-label">数量</span><span class="qr-value">{{ scanResult.qty }} 件</span></div>
        <div class="qr-row"><span class="qr-label">二维码</span><span class="qr-value" style="font-size:11px;word-break:break-all">{{ scanResult.barcode }}</span></div>
      </div>
      <div v-if="scanError" class="scan-error">
        <el-icon :size="16"><WarningFilled /></el-icon>
        <span>{{ scanError }}</span>
      </div>

      <BarcodeScanner ref="scannerRef" @scanned="onBarcodeScanned" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Box, Sell, Lock, Unlock, Camera, Upload, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { scanInbound } from '@/api/inbound'
import { unifiedScan } from '@/api/outbound'
import { sealBarcodes, unsealBarcode } from '@/api/freeze'
import BarcodeScanner from '@/components/BarcodeScanner.vue'

const scanMode = ref('inbound')
const scanResult = ref(null)
const scanError = ref('')
const scannerRef = ref(null)

const scanModeLabel = computed(() => ({ inbound: '入库', outbound: '出库', seal: '封存', unseal: '解封' }[scanMode.value] || ''))

function switchScanMode(mode) { scanMode.value = mode; scanResult.value = null; scanError.value = '' }
function openScanCamera() { scanResult.value = null; scanError.value = ''; scannerRef.value?.openCamera() }
function openScanUpload() { scanResult.value = null; scanError.value = ''; scannerRef.value?.openUpload() }

function parseMaterialFromBarcode(code) {
  if (!code || !code.startsWith('WMS|')) return '—'
  return code.split('|')[1] || '—'
}
function parseQtyFromBarcode(code) {
  if (!code || !code.startsWith('WMS|')) return 0
  return parseInt(code.split('|')[4]) || 0
}

async function onBarcodeScanned(code) {
  scanResult.value = null; scanError.value = ''
  const isInbound = code.startsWith('WMS|'), isOutbound = code.startsWith('OUT|')
  try {
    switch (scanMode.value) {
      case 'inbound':
        if (isOutbound) { scanError.value = '这是出库标签，请切换到出库模式'; return }
        const ibData = await scanInbound({ barcode: code })
        scanResult.value = { ...ibData, barcode: code }
        ElMessage.success(`入库成功：${ibData.materialCode}，${ibData.qty} 件`)
        break
      case 'outbound':
        if (!isInbound) { scanError.value = '出库请扫描入库二维码（WMS|...），OUT标签已废弃'; return }
        const obData = await unifiedScan({ barcode: code })
        scanResult.value = { ...obData, barcode: code }
        ElMessage.success(`出库成功：${obData.materialCode}，${obData.qty} 件`)
        break
      case 'seal':
        if (isOutbound) { scanError.value = '出库标签不可封存'; return }
        await sealBarcodes({ barcodes: [code], freezeType: '扫码', reason: '扫码封存' })
        scanResult.value = { materialCode: parseMaterialFromBarcode(code), qty: parseQtyFromBarcode(code), barcode: code }
        ElMessage.success(`封存成功：${scanResult.value.materialCode}`)
        break
      case 'unseal':
        if (isOutbound) { scanError.value = '出库标签不可解封'; return }
        await unsealBarcode(code)
        scanResult.value = { materialCode: parseMaterialFromBarcode(code), qty: parseQtyFromBarcode(code), barcode: code }
        ElMessage.success(`解封成功：${scanResult.value.materialCode}`)
        break
    }
  } catch (err) { scanError.value = err.message || '操作失败' }
}
</script>

<style scoped>
.scan-mode-row { display: flex; gap: 16px; margin-bottom: 20px; }
.scan-mode-item {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 14px 28px; border: 2px solid var(--border-light); border-radius: 8px;
  cursor: pointer; transition: all 0.2s; font-size: 15px; font-weight: 500;
  color: var(--text-secondary); user-select: none; flex: 1;
}
.scan-mode-item:hover { border-color: var(--wms-primary); color: var(--wms-primary); background: #ecf5ff; }
.scan-mode-item.active { border-color: var(--wms-primary); background: #ecf5ff; color: var(--wms-primary); box-shadow: 0 0 0 2px rgba(64,158,255,0.15); }

.scan-action-row { display: flex; gap: 20px; margin-bottom: 16px; }
.scan-action-btn {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 28px 40px; border: 2px dashed var(--border-base); border-radius: 12px;
  cursor: pointer; transition: all 0.2s; color: var(--text-secondary); flex: 1; font-size: 14px;
}
.scan-action-btn:hover { border-color: var(--wms-primary); color: var(--wms-primary); background: #ecf5ff; }

.scan-result { margin-top: 16px; padding: 16px; border-radius: 4px; }
.scan-result-header { display: flex; align-items: center; gap: 6px; font-size: 15px; font-weight: 600; margin-bottom: 10px; padding-bottom: 10px; border-bottom: 1px solid rgba(0,0,0,0.06); }
.scan-inbound { background: #f0f9eb; border: 1px solid #e1f3d8; } .scan-inbound .scan-result-header { color: #67c23a; }
.scan-outbound { background: #f4f4f5; border: 1px solid #e4e7ed; } .scan-outbound .scan-result-header { color: #909399; }
.scan-seal { background: #fdf6ec; border: 1px solid #faecd8; } .scan-seal .scan-result-header { color: #e6a23c; }
.scan-unseal { background: #ecf5ff; border: 1px solid #d9ecff; } .scan-unseal .scan-result-header { color: #409eff; }
.scan-error { margin-top: 16px; padding: 12px 16px; background: #fef0f0; border-radius: 4px; color: #f56c6c; font-size: 13px; display: flex; align-items: center; gap: 6px; }

.qr-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.qr-label { color: var(--text-secondary); font-size: 13px; }
.qr-value { color: var(--text-primary); font-size: 13px; font-weight: 500; }
</style>
