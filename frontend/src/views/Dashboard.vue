<!--
  智能库存看板首页 — 工作台风格（含图表 + 动态流 + 实时告警）。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <!-- 统计概览 -->
    <div class="stat-row">
      <div class="stat-item">
        <span class="stat-number">{{ stats.totalSku }}</span>
        <span class="stat-desc">总物料 SKU 数</span>
      </div>
      <div class="stat-item stat-warn">
        <span class="stat-number">{{ stats.deadStockCount }}</span>
        <span class="stat-desc">呆滞物料</span>
      </div>
      <div class="stat-item stat-danger">
        <span class="stat-number">{{ stats.highRiskCount }}</span>
        <span class="stat-desc">断供高风险物料</span>
      </div>
      <div class="stat-spacer"></div>
      <div class="stat-tip">
        <span>最后更新：{{ lastUpdateTime }}</span>
        <el-button text size="small" @click="loadData">刷新数据</el-button>

      </div>
    </div>


    <!-- 图表区域 -->
    <div class="chart-row">
      <div class="chart-half">
        <ChartCard title="库存水位分布" :height="260" :option="levelPieOption" />
      </div>
      <div class="chart-half">
        <ChartCard title="库存量 Top 10" :height="260" :option="stockBarOption" />
      </div>
    </div>

    <!-- 扫码操作面板 -->
    <div class="content-block scan-block">
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
          <span>摄像头扫码</span>
        </div>
        <div class="scan-action-btn" @click="openScanUpload">
          <el-icon :size="28"><Upload /></el-icon>
          <span>上传图片</span>
        </div>
      </div>

      <!-- 模式说明 -->
      <div class="scan-mode-hint">
        <template v-if="scanMode === 'inbound'">扫描入库条码（WMS|... 格式），自动核销入库并更新库存</template>
        <template v-else-if="scanMode === 'outbound'">扫描已拣货的入库条码（WMS|...），核销出库（一码到底）</template>
        <template v-else-if="scanMode === 'seal'">扫描在库条码将其封存，封存后该条码不可出库</template>
        <template v-else>扫描已封存条码将其解封，恢复为在库可流转状态</template>
      </div>

      <!-- 扫码结果 -->
      <div v-if="scanResult" class="scan-result" :class="'scan-' + scanMode">
        <div class="scan-result-header">
          <el-icon :size="18"><CircleCheckFilled /></el-icon>
          <span>{{ scanModeLabel }}成功</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">物料号</span>
          <span class="qr-value">{{ scanResult.materialCode }}</span>
        </div>
        <div class="qr-row" v-if="scanResult.orderNo">
          <span class="qr-label">{{ scanMode === 'outbound' ? '出库单号' : '入库单号' }}</span>
          <span class="qr-value">{{ scanResult.orderNo }}</span>
        </div>
        <div class="qr-row" v-if="scanResult.qty">
          <span class="qr-label">数量</span>
          <span class="qr-value">{{ scanResult.qty }} 件</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">条码</span>
          <span class="qr-value" style="font-size:11px;word-break:break-all">{{ scanResult.barcode }}</span>
        </div>
      </div>
      <div v-if="scanError" class="scan-error">
        <el-icon :size="16"><WarningFilled /></el-icon>
        <span>{{ scanError }}</span>
      </div>
      <div style="margin-top: 10px">
        <el-button type="primary" link size="small" @click="$router.push('/inventory-trace')">
          <el-icon :size="14"><Search /></el-icon>
          库存追溯查询
        </el-button>
      </div>
    </div>

    <BarcodeScanner ref="scannerRef" @scanned="onBarcodeScanned" />

    <!-- 双栏工作区 -->
    <div class="work-area">
      <!-- 左栏：库存水位分布 -->
      <div class="content-block work-left">
        <div class="block-header">
          <span class="block-title">库存水位分布</span>
          <el-input v-model="searchKeyword" placeholder="搜索物料" clearable size="small"
            style="width: 220px" />
        </div>
        <el-table :data="filteredData" stripe size="small">
          <el-table-column prop="materialCode" label="物料号" width="130" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="stockQty" label="当前库存" width="90" align="right" />
          <el-table-column prop="minStockDays" label="低储(天)" width="80" align="center" />
          <el-table-column prop="maxStockDays" label="高储(天)" width="80" align="center" />
          <el-table-column label="评级" width="100" align="center">
            <template #default="{ row }">
              <span class="badge" :class="'badge-' + badgeClass(row.ruleEvaluation)">
                {{ ruleLabel(row.ruleEvaluation) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 右栏：AI 速查 + 最近动态 -->
      <div class="content-block work-right" style="width: 360px; flex-shrink: 0">
        <div class="block-header"><span class="block-title">AI 智能速查</span></div>
        <div style="display: flex; gap: 8px; margin-bottom: 16px">
          <el-input v-model="quickCode" placeholder="输入物料号" size="small" clearable />
          <el-button type="primary" size="small" :loading="quickLoading" @click="handleQuickSearch">查询</el-button>
        </div>
        <div v-if="quickResult" class="quick-result">
          <div class="qr-row"><span class="qr-label">物料</span><span class="qr-value">{{ quickResult.materialCode }}</span></div>
          <div class="qr-row"><span class="qr-label">快照库存</span><span class="qr-value">{{ quickResult.currentStock }} 件</span></div>
          <div class="qr-row"><span class="qr-label">风险类型</span><span class="badge" :class="'badge-' + badgeClass(quickResult.riskType)">{{ riskLabel(quickResult.riskType) }}</span></div>
          <div class="qr-row"><span class="qr-label">风险等级</span><span class="qr-value">{{ quickResult.riskLevel }}</span></div>
          <div class="qr-row"><span class="qr-label">建议补货</span><span class="qr-value" style="font-weight: 600; color: var(--wms-primary)">{{ quickResult.suggestedQty }} 件</span></div>
          <div class="qr-row"><span class="qr-label">置信度</span><span class="qr-value">{{ (quickResult.confidence * 100).toFixed(0) }}%</span></div>
          <el-divider style="margin: 12px 0" />
          <p class="qr-advice">{{ quickResult.replenishmentSuggestion }}</p>
        </div>
        <div v-else-if="quickSearched" class="empty-hint">
          <p>暂无该物料的 AI 分析报告</p>
          <el-button text type="primary" size="small" @click="handleTriggerPredict">点击发起 AI 预测</el-button>
        </div>
        <div v-else class="empty-hint"><p>输入物料号查看 AI 智能分析结果</p></div>

        <!-- 最近动态 -->
        <el-divider style="margin: 20px 0 12px" />
        <div class="block-header" style="margin-bottom: 8px"><span class="block-title">最近动态</span></div>
        <div v-if="recentActivity.length" class="activity-list">
          <div v-for="(a, i) in recentActivity" :key="i" class="activity-item">
            <span class="act-badge" :class="a.type === 'inbound' ? 'badge-success' : 'badge-warn'">
              {{ a.type === 'inbound' ? '入库' : '出库' }}
            </span>
            <span class="act-text">{{ a.text }}</span>
            <span class="act-time">{{ a.time }}</span>
          </div>
        </div>
        <div v-else class="empty-hint" style="padding: 20px 0"><p>暂无最近动态</p></div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 智能看板 — 统计概览 + 图表 + 库存水位 + AI 速查 + 扫码入库 + 动态流 + 实时告警。
 */
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { getStockReport } from '@/api/stock'
import { getLatestReport, triggerPredict } from '@/api/ai'
import { getInboundOrders, scanInbound } from '@/api/inbound'
import { getOutboundOrders, unifiedScan } from '@/api/outbound'
import { sealBarcodes, unsealBarcode } from '@/api/freeze'
import { ElMessage, ElNotification } from 'element-plus'
import { WarningFilled, Camera, Upload, Search, Box, Sell, Lock, Unlock, CircleCheckFilled } from '@element-plus/icons-vue'
import BarcodeScanner from '@/components/BarcodeScanner.vue'
import ChartCard from '@/components/ChartCard.vue'

const stats = reactive({ totalSku: 0, deadStockCount: 0, highRiskCount: 0 })
const lastUpdateTime = ref('—')
const stockData = ref([])
const searchKeyword = ref('')
const filteredData = computed(() => {
  if (!searchKeyword.value) return stockData.value
  const kw = searchKeyword.value.toLowerCase()
  return stockData.value.filter(r => r.materialCode?.toLowerCase().includes(kw) || r.materialName?.toLowerCase().includes(kw))
})

// AI 速查
const quickCode = ref('')
const quickLoading = ref(false)
const quickResult = ref(null)
const quickSearched = ref(false)

// 扫码操作
const scanMode = ref('inbound')
const scanResult = ref(null)
const scanError = ref('')
const scannerRef = ref(null)

const scanModeLabel = computed(() => {
  const map = { inbound: '入库', outbound: '出库', seal: '封存', unseal: '解封' }
  return map[scanMode.value] || ''
})

function switchScanMode(mode) {
  scanMode.value = mode
  scanResult.value = null
  scanError.value = ''
}

function openScanCamera() {
  scanResult.value = null
  scanError.value = ''
  scannerRef.value?.openCamera()
}

function openScanUpload() {
  scanResult.value = null
  scanError.value = ''
  scannerRef.value?.openUpload()
}

// 最近动态
const recentActivity = ref([])

// 实时告警：记录上一次检测到的低储物料集合，用于去重
let lastLowStockSet = new Set()
let alertTimer = null

// ==================== 图表配置 ====================
const levelPieOption = computed(() => {
  const counts = { NORMAL: 0, LOW_STOCK: 0, HIGH: 0, DEAD_STOCK: 0 }
  stockData.value.forEach(r => { if (counts[r.ruleEvaluation] !== undefined) counts[r.ruleEvaluation]++ })
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 种 ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['45%', '72%'], center: ['50%', '48%'],
      label: { formatter: '{b}\n{d}%', fontSize: 11 },
      data: [
        { value: counts.NORMAL, name: '正常', itemStyle: { color: '#67c23a' } },
        { value: counts.LOW_STOCK, name: '超低储', itemStyle: { color: '#f56c6c' } },
        { value: counts.HIGH, name: '超高储', itemStyle: { color: '#e6a23c' } },
        { value: counts.DEAD_STOCK, name: '滞销', itemStyle: { color: '#f0ad4e' } }
      ]
    }]
  }
})

const stockBarOption = computed(() => {
  const top10 = [...stockData.value].sort((a, b) => b.stockQty - a.stockQty).slice(0, 10)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 10, bottom: 50 },
    xAxis: {
      type: 'category', data: top10.map(r => r.materialCode?.substring(7) || ''),
      axisLabel: { rotate: 45, fontSize: 10 }
    },
    yAxis: { type: 'value', name: '件' },
    series: [{
      type: 'bar', data: top10.map(r => r.stockQty),
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: params => {
          const v = top10[params.dataIndex]?.ruleEvaluation
          return v === 'LOW_STOCK' ? '#f56c6c' : v === 'HIGH' ? '#e6a23c' : v === 'DEAD_STOCK' ? '#f0ad4e' : '#67c23a'
        }
      }
    }]
  }
})

onMounted(() => {
  loadData()
  startAlertPolling()
})

onUnmounted(() => {
  if (alertTimer) clearInterval(alertTimer)
})

async function loadData() {
  try {
    const data = await getStockReport({})
    stockData.value = data || []
    stats.totalSku = stockData.value.length
    stats.deadStockCount = stockData.value.filter(r => r.ruleEvaluation === 'DEAD_STOCK').length
    stats.highRiskCount = stockData.value.filter(r => r.ruleEvaluation === 'LOW_STOCK').length
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN')

    // 加载最近动态
    await loadRecentActivity()
  } catch { /* */ }
}

// ==================== 最近动态 ====================
async function loadRecentActivity() {
  try {
    const [inRes, outRes] = await Promise.all([
      getInboundOrders({ page: 1, size: 8 }),
      getOutboundOrders({ page: 1, size: 8 })
    ])
    const items = []
    ;(inRes.records || []).forEach(r => {
      items.push({
        type: 'inbound',
        text: `入库单 ${r.orderNo} — ${r.status}`,
        time: r.createdAt?.substring(0, 16) || '',
        ts: new Date(r.createdAt).getTime()
      })
    })
    ;(outRes.records || []).forEach(r => {
      items.push({
        type: 'outbound',
        text: `出库单 ${r.orderNo} — ${r.status}`,
        time: r.createdAt?.substring(0, 16) || '',
        ts: new Date(r.createdAt).getTime()
      })
    })
    items.sort((a, b) => b.ts - a.ts)
    recentActivity.value = items.slice(0, 10)
  } catch { /* */ }
}

// ==================== 实时告警轮询 ====================
function startAlertPolling() {
  alertTimer = setInterval(async () => {
    try {
      const data = await getStockReport({})
      const currentLowSet = new Set()
      ;(data || []).forEach(r => {
        if (r.ruleEvaluation === 'LOW_STOCK') currentLowSet.add(r.materialCode)
      })
      // 检测新增的低储物料
      const newLows = [...currentLowSet].filter(c => !lastLowStockSet.has(c))
      if (newLows.length) {
        ElNotification({
          title: '⚠ 库存预警',
          message: `以下物料触发超低储预警：${newLows.join(', ')}`,
          type: 'warning',
          duration: 10000
        })
      }
      lastLowStockSet = currentLowSet
    } catch { /* */ }
  }, 60000)
}

/** 从 WMS|... 条码中解析物料编码 */
function parseMaterialFromBarcode(code) {
  if (!code || !code.startsWith('WMS|')) return '—'
  const parts = code.split('|')
  return parts[1] || '—'
}

/** 从 WMS|... 条码中解析单箱容量 */
function parseQtyFromBarcode(code) {
  if (!code || !code.startsWith('WMS|')) return 0
  const parts = code.split('|')
  return parseInt(parts[4]) || 0
}

// ==================== 扫码操作（入库/出库/封存/解封，严格隔离） ====================
async function onBarcodeScanned(code) {
  scanResult.value = null
  scanError.value = ''
  try {
    // —— 格式校验：根据当前模式拒绝不匹配的条码格式 ——
    const isInboundBarcode = code.startsWith('WMS|')
    const isOutboundBarcode = code.startsWith('OUT|')

    switch (scanMode.value) {
      case 'inbound': {
        if (isOutboundBarcode) {
          scanError.value = '这是出库标签（OUT|...），请切换到「出库」模式扫码'
          return
        }
        const data = await scanInbound({ barcode: code })
        scanResult.value = { ...data, barcode: code }
        ElMessage.success(`入库成功：${data.materialCode}，${data.qty} 件`)
        loadData()
        break
      }
      case 'outbound': {
        if (!isInboundBarcode) {
          scanError.value = '出库请扫描入库条码（WMS|...），OUT标签已废弃。请切换到对应模式或使用正确的条码'
          return
        }
        const data = await unifiedScan({ barcode: code })
        scanResult.value = { ...data, barcode: code }
        ElMessage.success(`出库成功：${data.materialCode}，${data.qty} 件`)
        loadData()
        break
      }
      case 'seal': {
        if (isOutboundBarcode) {
          scanError.value = '出库标签不可封存，请扫描在库条码（WMS|...）'
          return
        }
        await sealBarcodes({ barcodes: [code], freezeType: '扫码', reason: '扫码封存' })
        scanResult.value = { materialCode: parseMaterialFromBarcode(code), qty: parseQtyFromBarcode(code), barcode: code }
        ElMessage.success(`封存成功：${scanResult.value.materialCode}`)
        break
      }
      case 'unseal': {
        if (isOutboundBarcode) {
          scanError.value = '出库标签不可解封，请扫描封存中的入库条码'
          return
        }
        await unsealBarcode(code)
        scanResult.value = { materialCode: parseMaterialFromBarcode(code), qty: parseQtyFromBarcode(code), barcode: code }
        ElMessage.success(`解封成功：${scanResult.value.materialCode}`)
        break
      }
    }
  } catch (err) {
    scanError.value = err.message || '扫码操作失败'
  }
}

async function handleQuickSearch() {
  if (!quickCode.value.trim()) return
  quickLoading.value = true
  quickSearched.value = true
  try { quickResult.value = await getLatestReport(quickCode.value.trim()) } catch { quickResult.value = null }
  finally { quickLoading.value = false }
}

async function handleTriggerPredict() {
  try { await triggerPredict(quickCode.value.trim()); ElMessage.success('AI 预测任务已启动') }
  catch { ElMessage.error('启动失败') }
}


// 辅助
function badgeClass(v) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'warn', 'HIGH': 'warn', 'BOTH': 'danger', 'CRITICAL': 'danger', 'NORMAL': 'success' }
  return m[v] || 'default'
}
function ruleLabel(v) {
  const m = { 'LOW_STOCK': '超低储', 'DEAD_STOCK': '滞销', 'HIGH': '超高储', 'NORMAL': '正常' }
  return m[v] || v
}
function riskLabel(v) {
  const m = { 'LOW_STOCK': '断供预警', 'DEAD_STOCK': '滞销风险', 'BOTH': '双重风险', 'NORMAL': '正常' }
  return m[v] || v
}
</script>

<style scoped>
.stat-row { display: flex; align-items: center; gap: 32px; background: var(--content-bg); padding: 20px 24px; border-radius: 4px; box-shadow: var(--shadow-light); margin-bottom: 16px; }
.stat-item { display: flex; flex-direction: column; }
.stat-spacer { flex: 1; }
.stat-tip { font-size: 12px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; }
.stat-number { font-size: 28px; font-weight: 700; color: var(--text-primary); line-height: 1.2; }
.stat-desc { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.stat-warn .stat-number { color: var(--wms-warning); }
.stat-danger .stat-number { color: var(--wms-danger); }

/* 图表 */
.chart-row { display: flex; gap: 16px; margin-bottom: 16px; }
.chart-half { flex: 1; min-width: 0; }

/* 扫码操作面板 */
.scan-block { margin-bottom: 16px; }

/* 模式选择行 */
.scan-mode-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.scan-mode-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: 2px solid var(--border-light);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  user-select: none;
}
.scan-mode-item:hover {
  border-color: var(--wms-primary);
  color: var(--wms-primary);
  background: #ecf5ff;
}
.scan-mode-item.active {
  border-color: var(--wms-primary);
  background: #ecf5ff;
  color: var(--wms-primary);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}

/* 扫描按钮行 */
.scan-action-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}
.scan-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 32px;
  border: 2px dashed var(--border-base);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--text-secondary);
  flex: 1;
}
.scan-action-btn:hover {
  border-color: var(--wms-primary);
  color: var(--wms-primary);
  background: #f5f9ff;
  border-style: solid;
}

/* 模式说明 */
.scan-mode-hint {
  font-size: 12px;
  color: var(--text-placeholder);
  text-align: center;
  margin-bottom: 12px;
  line-height: 1.5;
}

/* 扫码结果 */
.scan-result { margin-top: 12px; padding: 12px; border-radius: 4px; }
.scan-result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(0,0,0,0.06);
}
.scan-inbound { background: #f0f9eb; border: 1px solid #e1f3d8; }
.scan-inbound .scan-result-header { color: #67c23a; }
.scan-outbound { background: #f4f4f5; border: 1px solid #e4e7ed; }
.scan-outbound .scan-result-header { color: #909399; }
.scan-seal { background: #fdf6ec; border: 1px solid #faecd8; }
.scan-seal .scan-result-header { color: #e6a23c; }
.scan-unseal { background: #ecf5ff; border: 1px solid #d9ecff; }
.scan-unseal .scan-result-header { color: #409eff; }
.scan-error { margin-top: 12px; padding: 10px 14px; background: #fef0f0; border-radius: 4px; color: #f56c6c; font-size: 13px; display: flex; align-items: center; gap: 6px; }

/* 工作区 */
.work-area { display: flex; gap: 16px; align-items: flex-start; }
.work-left { flex: 1; min-width: 0; }
.work-right { width: 360px; flex-shrink: 0; }

/* 动态 */
.activity-list { font-size: 12px; }
.activity-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.act-badge { display: inline-block; padding: 1px 6px; border-radius: 2px; font-size: 11px; white-space: nowrap; color: #fff; }
.act-badge.badge-success { background: #67c23a; }
.act-badge.badge-warn { background: #e6a23c; }
.act-text { flex: 1; color: var(--text-regular); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.act-time { color: var(--text-secondary); white-space: nowrap; font-size: 11px; }

/* 速查 */
.quick-result { font-size: 13px; }
.qr-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid var(--border-light); }
.qr-label { color: var(--text-secondary); }
.qr-value { color: var(--text-primary); }
.qr-advice { font-size: 13px; color: var(--text-regular); line-height: 1.7; }

.badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 12px; font-weight: 500; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-danger  { background: #fef0f0; color: #f56c6c; }
.badge-default { background: #f4f4f5; color: #909399; }

/* 响应式布局 */
@media (max-width: 900px) {
  .chart-row,
  .work-area {
    flex-direction: column;
  }

  .chart-half,
  .work-left,
  .work-right {
    width: 100% !important;
  }
}

@media (max-width: 768px) {
  .stat-row {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    padding: 14px;
  }

  .stat-spacer {
    display: none;
  }

  .stat-tip {
    grid-column: 1 / -1;
    align-items: flex-start;
  }

  .scan-row {
    align-items: stretch;
    flex-direction: column;
  }

  .activity-item {
    align-items: flex-start;
  }

  .act-time {
    display: none;
  }
}

@media (max-width: 420px) {
  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>
