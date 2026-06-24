<!--
  库存报表与风险预警页（含图表可视化 + 导出）。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <!-- 图表区 -->
    <div class="chart-row">
      <div class="chart-half">
        <ChartCard title="库存量分布" :height="280" :option="barOption" />
      </div>
      <div class="chart-half">
        <ChartCard title="水位分布" :height="280" :option="pieOption" />
      </div>
    </div>

    <div class="content-block">
      <div class="toolbar">
        <el-input v-model="filterCode" placeholder="物料号模糊检索" clearable size="small"
          style="width: 220px" @input="loadReport" />
        <el-select v-model="filterStatus" placeholder="水位状态" clearable size="small"
          style="width: 140px" @change="loadReport">
          <el-option label="正常" value="NORMAL" />
          <el-option label="低储" value="LOW" />
          <el-option label="高储" value="HIGH" />
          <el-option label="呆滞" value="DEAD_STOCK" />
        </el-select>
        <el-button size="small" @click="loadReport">刷新</el-button>
        <el-button size="small" @click="doExport">导出 CSV</el-button>
        <span class="toolbar-tip">共 {{ reportData.length }} 条记录</span>
      </div>

      <el-table :data="reportData" stripe size="small" v-loading="loading"
        style="width: 100%">
        <el-table-column prop="materialCode" label="物料号" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" />
        <el-table-column prop="stockQty" label="当前库存" width="80" align="right" />
        <el-table-column label="日均消耗" width="75" align="right">
          <template #default="{ row }">{{ row.dailyConsume != null ? row.dailyConsume.toFixed(1) : '—' }}</template>
        </el-table-column>
        <el-table-column label="DOHF(天)" width="75" align="right">
          <template #default="{ row }">
            <span v-if="row.dohf != null && row.dohf > 0 && row.dohf < 9999">{{ row.dohf.toFixed(1) }}</span>
            <span v-else-if="row.dohf >= 9999" style="color:#e6a23c">极高</span>
            <span v-else style="color:#909399">—</span>
          </template>
        </el-table-column>
        <el-table-column label="安全库存" width="70" align="right">
          <template #default="{ row }">{{ row.safetyStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="提前期(天)" width="75" align="center">
          <template #default="{ row }">{{ row.leadTimeDays ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="最后出库" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.lastOutboundDate">{{ row.lastOutboundDate?.substring(0, 10) }}</span>
            <span v-else style="color:#909399">无记录</span>
          </template>
        </el-table-column>
        <el-table-column label="闲置天数" width="72" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.idleDays >= 90 ? '#f56c6c' : '' }">{{ row.idleDays ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评级" width="75" align="center">
          <template #default="{ row }">
            <span class="badge" :class="'badge-' + badgeClass(row.ruleEvaluation)">
              {{ ruleLabel(row.ruleEvaluation) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small"
              :loading="predicting === row.materialCode"
              @click="handlePredict(row)">AI 推演</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
/**
 * 库存报表 — 图表 + 筛选 + 颜色高亮 + AI 推演 + 导出。
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStockReport } from '@/api/stock'
import { triggerPredict } from '@/api/ai'
import { exportCSV } from '@/utils/export'
import ChartCard from '@/components/ChartCard.vue'

const reportData = ref([])
const loading = ref(false)
const predicting = ref(null)
const filterCode = ref('')
const filterStatus = ref('')

onMounted(() => loadReport())

async function loadReport() {
  loading.value = true
  try {
    const data = await getStockReport({ materialCode: filterCode.value || undefined, alarmStatus: filterStatus.value || undefined })
    reportData.value = data || []
  } catch { /* */ } finally { loading.value = false }
}

async function handlePredict(row) {
  predicting.value = row.materialCode
  try { await triggerPredict(row.materialCode); ElMessage.success(`物料 ${row.materialCode} AI 预测已启动`) }
  catch { ElMessage.error('启动失败') } finally { predicting.value = null }
}

function badgeClass(v) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'dead', 'HIGH': 'warning', 'NORMAL': 'success' }
  return m[v] || 'default'
}
function ruleLabel(v) {
  const m = { 'LOW_STOCK': '低储', 'DEAD_STOCK': '呆滞', 'HIGH': '高储', 'NORMAL': '正常' }
  return m[v] || v
}

// ==================== 图表 ====================
const barOption = computed(() => {
  const sorted = [...reportData.value].sort((a, b) => b.stockQty - a.stockQty).slice(0, 15)
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 45, right: 20, top: 10, bottom: 60 },
    xAxis: { type: 'category', data: sorted.map(r => r.materialCode?.substring(7) || ''), axisLabel: { rotate: 45, fontSize: 10 } },
    yAxis: { type: 'value', name: '件' },
    series: [{
      type: 'bar', data: sorted.map(r => r.stockQty),
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: params => {
          const v = sorted[params.dataIndex]?.ruleEvaluation
          return v === 'LOW_STOCK' ? '#f56c6c' : v === 'HIGH' ? '#e6a23c' : v === 'DEAD_STOCK' ? '#606266' : '#67c23a'
        }
      }
    }]
  }
})

const pieOption = computed(() => {
  const counts = { NORMAL: 0, LOW_STOCK: 0, HIGH: 0, DEAD_STOCK: 0 }
  reportData.value.forEach(r => { if (counts[r.ruleEvaluation] !== undefined) counts[r.ruleEvaluation]++ })
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

function doExport() {
  if (!reportData.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'materialCode', label: '物料号' },
    { key: 'materialName', label: '物料名称' },
    { key: 'stockQty', label: '当前库存' },
    { key: 'dailyConsume', label: '日均消耗' },
    { key: 'dohf', label: 'DOHF(天)' },
    { key: 'safetyStock', label: '安全库存' },
    { key: 'leadTimeDays', label: '提前期(天)' },
    { key: 'lastOutboundDate', label: '最后出库' },
    { key: 'idleDays', label: '闲置天数' },
    { key: 'ruleEvaluation', label: '评级' }
  ], reportData.value, `库存报表_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
</script>

<style scoped>
.chart-row { display: flex; gap: 16px; margin-bottom: 16px; }
.chart-half { flex: 1; min-width: 0; }
.toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--text-secondary); margin-left: auto; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 12px; font-weight: 500; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warning { background: #fdf6ec; color: #e6a23c; }
.badge-danger  { background: #fef0f0; color: #f56c6c; }
.badge-dead    { background: #f0f0f0; color: #606266; }
.badge-default { background: #f4f4f5; color: #909399; }

@media (max-width: 900px) {
  .chart-row { flex-direction: column; }
}

@media (max-width: 768px) {
  .toolbar {
    align-items: stretch;
  }

  .toolbar-tip {
    order: 10;
  }
}
</style>
