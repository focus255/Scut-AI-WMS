<!--
  智能库存看板首页 — 工作台风格。
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
        <el-button text size="small" @click="doPrintDashboard">打印看板</el-button>
      </div>
    </div>

    <!-- 打印专用标题（仅打印时显示） -->
    <div class="print-only-header">
      <h2>智库 WMS — 库存看板</h2>
      <span>打印时间：{{ new Date().toLocaleString('zh-CN') }}</span>
    </div>

    <!-- 扫码入库快捷入口 -->
    <div class="content-block scan-block">
      <div class="block-header">
        <span class="block-title">扫码入库</span>
        <span class="scan-hint">扫描条码或手动输入后按回车确认</span>
      </div>
      <div class="scan-input-area">
        <el-input ref="scanInputRef" v-model="scanCode" placeholder="扫描或输入条码，按回车确认入库"
          size="large" clearable @keyup.enter="handleScanInbound"
          :loading="scanLoading">
          <template #prefix>
            <el-icon :size="18"><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div v-if="scanResult" class="scan-result">
        <div class="qr-row">
          <span class="qr-label">条码</span>
          <span class="qr-value">{{ scanResult.barcode }}</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">物料号</span>
          <span class="qr-value">{{ scanResult.materialCode }}</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">供应商</span>
          <span class="qr-value">{{ scanResult.supplierCode }}</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">入库单号</span>
          <span class="qr-value">{{ scanResult.orderNo }}</span>
        </div>
        <div class="qr-row">
          <span class="qr-label">状态</span>
          <span class="badge badge-success">已确认入库</span>
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

    <!-- 双栏工作区 -->
    <div class="work-area">
      <!-- 左栏：库存水位分布 -->
      <div class="content-block work-left">
        <div class="block-header">
          <span class="block-title">库存水位分布</span>
          <el-input v-model="searchKeyword" placeholder="搜索物料" clearable size="small"
            style="width: 220px" @input="filterTable" />
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

      <!-- 右栏：AI 速查 -->
      <div class="content-block work-right" style="width: 340px; flex-shrink: 0">
        <div class="block-header">
          <span class="block-title">AI 智能速查</span>
        </div>
        <div style="display: flex; gap: 8px; margin-bottom: 16px">
          <el-input v-model="quickCode" placeholder="输入物料号" size="small" clearable />
          <el-button type="primary" size="small" :loading="quickLoading" @click="handleQuickSearch">
            查询
          </el-button>
        </div>

        <div v-if="quickResult" class="quick-result">
          <div class="qr-row">
            <span class="qr-label">物料</span>
            <span class="qr-value">{{ quickResult.materialCode }}</span>
          </div>
          <div class="qr-row">
            <span class="qr-label">快照库存</span>
            <span class="qr-value">{{ quickResult.currentStock }} 件</span>
          </div>
          <div class="qr-row">
            <span class="qr-label">风险类型</span>
            <span class="badge" :class="'badge-' + badgeClass(quickResult.riskType)">
              {{ riskLabel(quickResult.riskType) }}
            </span>
          </div>
          <div class="qr-row">
            <span class="qr-label">风险等级</span>
            <span class="qr-value">{{ quickResult.riskLevel }}</span>
          </div>
          <div class="qr-row">
            <span class="qr-label">建议补货</span>
            <span class="qr-value" style="font-weight: 600; color: var(--wms-primary)">
              {{ quickResult.suggestedQty }} 件
            </span>
          </div>
          <div class="qr-row">
            <span class="qr-label">置信度</span>
            <span class="qr-value">{{ (quickResult.confidence * 100).toFixed(0) }}%</span>
          </div>
          <el-divider style="margin: 12px 0" />
          <p class="qr-advice">{{ quickResult.replenishmentSuggestion }}</p>
        </div>
        <div v-else-if="quickSearched" class="empty-hint">
          <p>暂无该物料的 AI 分析报告</p>
          <el-button text type="primary" size="small" @click="handleTriggerPredict">
            点击发起 AI 预测
          </el-button>
        </div>
        <div v-else class="empty-hint">
          <p>输入物料号查看 AI 智能分析结果</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 智能看板 — 统计概览 + 库存水位 + AI 速查 + 扫码入库。
 */
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { getStockReport } from '@/api/stock'
import { getLatestReport, triggerPredict } from '@/api/ai'
import { scanInbound } from '@/api/inbound'
import { ElMessage } from 'element-plus'
import { Search, WarningFilled } from '@element-plus/icons-vue'

// 统计
const stats = reactive({ totalSku: 0, deadStockCount: 0, highRiskCount: 0 })
const lastUpdateTime = ref('—')

// 左栏表格
const stockData = ref([])
const searchKeyword = ref('')
const filteredData = computed(() => {
  if (!searchKeyword.value) return stockData.value
  const kw = searchKeyword.value.toLowerCase()
  return stockData.value.filter(
    r => r.materialCode?.toLowerCase().includes(kw) ||
         r.materialName?.toLowerCase().includes(kw)
  )
})

// 右栏速查
const quickCode = ref('')
const quickLoading = ref(false)
const quickResult = ref(null)
const quickSearched = ref(false)

// ==================== 扫码入库 ====================
const scanInputRef = ref(null)
const scanCode = ref('')
const scanLoading = ref(false)
const scanResult = ref(null)
const scanError = ref('')

onMounted(() => {
  loadData()
  // 自动聚焦扫码输入框
  nextTick(() => {
    setTimeout(() => scanInputRef.value?.focus(), 600)
  })
})

async function loadData() {
  try {
    const data = await getStockReport({})
    stockData.value = data || []
    stats.totalSku = stockData.value.length
    stats.deadStockCount = stockData.value.filter(r => r.ruleEvaluation === 'DEAD_STOCK').length
    stats.highRiskCount = stockData.value.filter(r => r.ruleEvaluation === 'LOW_STOCK').length
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN')
  } catch { /* 后端未就绪 */ }
}

function filterTable() { /* computed 自动处理 */ }

async function handleQuickSearch() {
  if (!quickCode.value.trim()) return
  quickLoading.value = true
  quickSearched.value = true
  try {
    quickResult.value = await getLatestReport(quickCode.value.trim())
  } catch {
    quickResult.value = null
  } finally {
    quickLoading.value = false
  }
}

async function handleTriggerPredict() {
  try {
    await triggerPredict(quickCode.value.trim())
    ElMessage.success('AI 预测任务已启动，请稍后刷新查看')
  } catch {
    ElMessage.error('启动失败')
  }
}

// ==================== 扫码入库 ====================
async function handleScanInbound() {
  if (!scanCode.value.trim()) return
  scanLoading.value = true
  scanResult.value = null
  scanError.value = ''
  try {
    const data = await scanInbound({ barcode: scanCode.value.trim() })
    scanResult.value = data
    scanCode.value = ''
    ElMessage.success(`条码 ${data.barcode} 入库成功`)
    // 入库后刷新库存数据
    loadData()
  } catch (err) {
    scanError.value = err.message || '扫码入库失败'
    setTimeout(() => {
      scanError.value = ''
      scanCode.value = ''
      scanInputRef.value?.focus()
    }, 3000)
  } finally {
    scanLoading.value = false
    // 重新聚焦输入框以接收下一次扫码
    nextTick(() => scanInputRef.value?.focus())
  }
}

// ==================== 打印看板 ====================
function doPrintDashboard() {
  window.print()
}

// ---- 辅助函数 ----
function badgeClass(v) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'warn', 'HIGH': 'warn',
    'BOTH': 'danger', 'CRITICAL': 'danger', 'NORMAL': 'success' }
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
/* 统计概览 */
.stat-row {
  display: flex;
  align-items: center;
  gap: 32px;
  background: var(--content-bg);
  padding: 20px 24px;
  border-radius: 4px;
  box-shadow: var(--shadow-light);
  margin-bottom: 16px;
}
.stat-item {
  display: flex;
  flex-direction: column;
}
.stat-spacer {
  flex: 1;
}
.stat-tip {
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 工作区 */
.work-area {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.work-left {
  flex: 1;
  min-width: 0;
}
.work-right {
  width: 340px;
  flex-shrink: 0;
}

/* 速查结果 */
.quick-result { font-size: 13px; }
.qr-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--border-light);
}
.qr-label { color: var(--text-secondary); }
.qr-value { color: var(--text-primary); }
.qr-advice {
  font-size: 13px;
  color: var(--text-regular);
  line-height: 1.7;
}

/* 状态徽章 */
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
}
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-danger  { background: #fef0f0; color: #f56c6c; }
.badge-default { background: #f4f4f5; color: #909399; }

/* ==================== 扫码入库 ==================== */
.scan-block {
  margin-bottom: 16px;
}
.scan-hint {
  font-size: 12px;
  color: var(--text-secondary);
}
.scan-input-area {
  max-width: 600px;
}
.scan-result {
  margin-top: 12px;
  padding: 12px;
  background: #f0f9eb;
  border-radius: 4px;
  border: 1px solid #e1f3d8;
}
.scan-error {
  margin-top: 12px;
  padding: 10px 14px;
  background: #fef0f0;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ==================== 打印专用 ==================== */
.print-only-header {
  display: none;
}
.print-only-header h2 {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
}
.print-only-header span {
  font-size: 13px;
  color: var(--text-secondary);
}

@media print {
  body {
    background: white !important;
  }
  .admin-sidebar,
  .admin-header,
  .stat-tip,
  .work-right,
  .scan-block,
  .scan-hint {
    display: none !important;
  }
  .admin-content {
    margin-left: 0 !important;
    padding: 0 !important;
  }
  .page-container {
    padding: 10px !important;
  }
  .content-block {
    box-shadow: none !important;
    border: 1px solid #ccc !important;
    break-inside: avoid;
  }
  .stat-row {
    break-inside: avoid;
    border: 1px solid #ccc;
    padding: 12px;
  }
  .work-area {
    flex-direction: column !important;
  }
  .work-left {
    width: 100% !important;
  }
  .print-only-header {
    display: block;
    text-align: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 2px solid #333;
  }
  .badge {
    border: 1px solid #999 !important;
  }
}
</style>
