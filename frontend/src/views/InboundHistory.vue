<!--
  入库历史查询页 — 日期范围筛选 + 汇总统计 + 趋势图 + 导出。
  @author Claude
  @date 2026-06-15
-->
<template>
  <div class="page-container">
    <!-- 汇总统计 -->
    <div class="stat-row">
      <div class="stat-item">
        <span class="stat-number">{{ summary.totalBatches }}</span>
        <span class="stat-desc">入库批次</span>
      </div>
      <div class="stat-item">
        <span class="stat-number">{{ summary.totalQty }}</span>
        <span class="stat-desc">入库总件数</span>
      </div>
      <div class="stat-spacer"></div>
      <div class="stat-tip">
        <span>最后更新：{{ lastUpdate }}</span>
        <el-button text size="small" @click="doQuery">刷新</el-button>
        <el-button text size="small" @click="doExport">导出 CSV</el-button>
      </div>
    </div>

    <!-- 趋势图 -->
    <ChartCard title="近7天入库趋势" :height="240" :option="trendOption" />

    <!-- 筛选栏 -->
    <div class="content-block">
      <div class="toolbar">
        <el-date-picker v-model="query.startDate" type="date" placeholder="开始日期"
          value-format="YYYY-MM-DD" size="small" style="width: 150px" />
        <span style="color: var(--text-secondary)">至</span>
        <el-date-picker v-model="query.endDate" type="date" placeholder="结束日期"
          value-format="YYYY-MM-DD" size="small" style="width: 150px" />
        <el-select v-model="query.status" placeholder="全部状态" clearable size="small" style="width: 120px">
          <el-option label="未入库" value="未入库" />
          <el-option label="已完成" value="已完成" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="单号/供应商" clearable size="small"
          style="width: 180px" @keyup.enter="doQuery" />
        <el-button type="primary" size="small" @click="doQuery" :loading="loading">查询</el-button>
        <span class="toolbar-tip">共 {{ total }} 条记录</span>
      </div>

      <el-table :data="records" stripe size="small" v-loading="loading"
        empty-text="暂无入库历史" @row-click="openDetail" style="cursor: pointer">
        <el-table-column prop="orderNo" label="入库单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="supplierCode" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
              {{ row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
      </el-table>
      <div style="margin-top: 12px; display: flex; justify-content: flex-end">
        <el-pagination
          v-if="total > size"
          :current-page="page" :page-size="size" :total="total"
          layout="total, prev, pager, next" small
          @current-change="(p) => { page = p; doQuery() }" />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <Teleport to="body">
      <el-dialog v-model="detailVisible" title="入库单详情" width="min(600px, calc(100vw - 32px))" destroy-on-close>
        <div v-if="detailData" class="detail-info-grid">
          <div class="detail-info-item">
            <span class="info-label">入库单号</span>
            <span class="info-value">{{ detailData.orderNo }}</span>
          </div>
          <div class="detail-info-item">
            <span class="info-label">供应商</span>
            <span class="info-value">{{ detailData.supplierCode }}</span>
          </div>
          <div class="detail-info-item">
            <span class="info-label">状态</span>
            <span class="badge" :class="detailData.status === '已完成' ? 'badge-success' : 'badge-default'">
              {{ detailData.status }}
            </span>
          </div>
          <div class="detail-info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ detailData.createdAt }}</span>
          </div>
        </div>
        <el-table v-if="detailData" :data="detailData.details" stripe size="small" style="margin-top: 16px">
          <el-table-column prop="materialCode" label="物料号" width="140" />
          <el-table-column prop="packCapacity" label="单箱容量" width="90" align="right" />
          <el-table-column prop="planQty" label="计划数" width="80" align="right" />
          <el-table-column prop="actualQty" label="实收数" width="80" align="right" />
        </el-table>
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 入库历史查询 — 筛选 + 趋势图 + 汇总 + 导出。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInboundHistory } from '@/api/inboundHistory'
import { getInboundDetail } from '@/api/inbound'
import { exportCSV } from '@/utils/export'
import ChartCard from '@/components/ChartCard.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const lastUpdate = ref('—')

const query = reactive({
  startDate: '',
  endDate: '',
  status: '',
  keyword: ''
})

const summary = reactive({
  totalBatches: 0,
  totalQty: 0,
  dailyTrend: []
})

// ECharts 近7天趋势
const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: {
    type: 'category',
    data: summary.dailyTrend.map(d => d.date?.substring(5) || ''),
    axisLabel: { fontSize: 11 }
  },
  yAxis: { type: 'value', name: '件数', minInterval: 1 },
  series: [{
    name: '入库量',
    type: 'bar',
    data: summary.dailyTrend.map(d => d.qty || 0),
    itemStyle: {
      color: '#409eff',
      borderRadius: [4, 4, 0, 0]
    }
  }]
}))

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref(null)

onMounted(() => doQuery())

async function doQuery() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (query.startDate) params.startDate = query.startDate
    if (query.endDate) params.endDate = query.endDate
    if (query.status) params.status = query.status
    if (query.keyword) params.keyword = query.keyword
    const data = await getInboundHistory(params)
    records.value = data.records || []
    total.value = data.total || 0
    if (data.summary) {
      summary.totalBatches = data.summary.totalBatches || 0
      summary.totalQty = data.summary.totalQty || 0
      summary.dailyTrend = data.summary.dailyTrend || []
    }
    lastUpdate.value = new Date().toLocaleTimeString('zh-CN')
  } catch { /* */ } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detailData.value = null
  detailVisible.value = true
  try {
    detailData.value = await getInboundDetail(row.id)
  } catch {
    detailVisible.value = false
  }
}

function doExport() {
  if (!records.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'orderNo', label: '入库单号' },
    { key: 'supplierCode', label: '供应商' },
    { key: 'status', label: '状态' },
    { key: 'createdAt', label: '创建时间' }
  ], records.value, `入库历史_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
</script>

<style scoped>
.stat-row {
  display: flex; align-items: center; gap: 32px;
  background: var(--content-bg); padding: 20px 24px;
  border-radius: 4px; box-shadow: var(--shadow-light); margin-bottom: 16px;
}
.stat-item { display: flex; flex-direction: column; }
.stat-spacer { flex: 1; }
.stat-tip { font-size: 12px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; }
.stat-number { font-size: 28px; font-weight: 700; color: var(--wms-primary); line-height: 1.2; }
.stat-desc { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--text-secondary); margin-left: auto; }

.badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 12px; white-space: nowrap; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-default { background: #f4f4f5; color: #909399; }

.detail-info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.detail-info-item { display: flex; flex-direction: column; gap: 4px; padding: 8px 12px; background: #fafafa; border-radius: 4px; }
.info-label { font-size: 12px; color: var(--text-secondary); }
.info-value { font-size: 14px; color: var(--text-primary); font-weight: 500; }
</style>
