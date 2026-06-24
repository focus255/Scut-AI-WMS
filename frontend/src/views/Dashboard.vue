<!--
  仪表盘首页 — 工作台风格（含图表 + 动态流 + 实时告警）。
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


    <!-- 图表区 (两列) -->
    <div class="dashboard-mid-row">
      <ChartCard title="库存水位分布" :height="260" :option="levelPieOption" />
      <ChartCard title="库存量 Top 10" :height="260" :option="stockBarOption" />
    </div>

    <!-- 库存 -->
    <div class="content-block" style="margin-bottom: 16px">
      <div class="block-header">
        <span class="block-title">库存</span>
        <el-input v-model="searchKeyword" placeholder="搜索物料" clearable size="small"
          style="width: 220px" />
      </div>
      <el-table :data="pagedStockData" stripe size="small">
        <el-table-column prop="materialCode" label="物料号" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="stockQty" label="当前库存" width="80" align="right" />
        <el-table-column label="日均消耗" width="75" align="right">
          <template #default="{ row }">{{ row.dailyConsume != null ? row.dailyConsume.toFixed(1) : '—' }}</template>
        </el-table-column>
        <el-table-column label="安全库存" width="70" align="right">
          <template #default="{ row }">{{ row.safetyStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="最后出库" width="95" align="center">
          <template #default="{ row }">
            <span v-if="row.lastOutboundDate">{{ row.lastOutboundDate?.substring(0, 10) }}</span>
            <span v-else style="color:#909399">无记录</span>
          </template>
        </el-table-column>
        <el-table-column label="评级" width="85" align="center">
          <template #default="{ row }">
            <span class="badge" :class="'badge-' + badgeClass(row.ruleEvaluation)">
              {{ ruleLabel(row.ruleEvaluation) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; display: flex; justify-content: flex-end">
        <el-pagination
          v-if="filteredData.length > stockPageSize"
          :current-page="stockPage" :page-size="stockPageSize" :total="filteredData.length"
          layout="total, prev, pager, next" size="small"
          @current-change="(p) => stockPage = p" />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 仪表盘 — 统计概览 + 图表 + 库存水位 + AI 速查 + 扫码入库 + 动态流 + 实时告警。
 */
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { getStockReport } from '@/api/stock'
import { getInboundOrders } from '@/api/inbound'
import { getOutboundOrders } from '@/api/outbound'
import { ElMessage, ElNotification } from 'element-plus'
import { WarningFilled, Search } from '@element-plus/icons-vue'
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

// 库存表分页
const stockPage = ref(1)
const stockPageSize = ref(10)
const pagedStockData = computed(() => {
  const start = (stockPage.value - 1) * stockPageSize.value
  return filteredData.value.slice(start, start + stockPageSize.value)
})
watch(searchKeyword, () => { stockPage.value = 1 })

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
        { value: counts.LOW_STOCK, name: '低储', itemStyle: { color: '#f56c6c' } },
        { value: counts.HIGH, name: '高储', itemStyle: { color: '#e6a23c' } },
        { value: counts.DEAD_STOCK, name: '呆滞', itemStyle: { color: '#606266' } }
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
          return v === 'LOW_STOCK' ? '#f56c6c' : v === 'HIGH' ? '#e6a23c' : v === 'DEAD_STOCK' ? '#606266' : '#67c23a'
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
          message: `以下物料触发低储预警：${newLows.join(', ')}`,
          type: 'warning',
          duration: 10000
        })
      }
      lastLowStockSet = currentLowSet
    } catch { /* */ }
  }, 60000)
}

// 辅助
function badgeClass(v) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'dead', 'HIGH': 'warning', 'BOTH': 'danger', 'CRITICAL': 'danger', 'NORMAL': 'success' }
  return m[v] || 'default'
}
function ruleLabel(v) {
  const m = { 'LOW_STOCK': '低储', 'HIGH': '高储', 'DEAD_STOCK': '呆滞', 'NORMAL': '正常' }
  return m[v] || v
}
</script>

<style scoped>
.stat-row { display: flex; align-items: center; gap: 28px; background: var(--content-bg); padding: 18px 24px; border-radius: 4px; box-shadow: var(--shadow-light); margin-bottom: 16px; }
.stat-item { display: flex; flex-direction: column; min-width: 100px; }
.stat-spacer { flex: 1; }
.stat-tip { font-size: 12px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.stat-number { font-size: 28px; font-weight: 700; color: var(--text-primary); line-height: 1.2; }
.stat-desc { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.stat-warn .stat-number, .stat-danger .stat-number { color: var(--wms-danger); }

/* ==================== 中栏：图表 + 扫码 ==================== */
.dashboard-mid-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

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
.badge-warning { background: #fdf6ec; color: #e6a23c; }
.badge-danger  { background: #fef0f0; color: #f56c6c; }
.badge-dead    { background: #f0f0f0; color: #606266; }
.badge-default { background: #f4f4f5; color: #909399; }

/* 响应式：中屏图表+扫码叠放 */
@media (max-width: 1100px) {
  .dashboard-mid-row {
    grid-template-columns: 1fr 1fr;
  }
  .dash-card-scan {
    grid-column: 1 / -1;
  }
}
@media (max-width: 768px) {
  .dashboard-mid-row {
    grid-template-columns: 1fr;
  }
  .stat-row {
    flex-wrap: wrap;
    gap: 12px;
  }
}

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
