<!--
  库存报表与风险预警页。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-input v-model="filterCode" placeholder="物料编码模糊检索" clearable size="small"
          style="width: 220px" @input="loadReport" />
        <el-select v-model="filterStatus" placeholder="水位状态" clearable size="small"
          style="width: 140px" @change="loadReport">
          <el-option label="正常" value="NORMAL" />
          <el-option label="超低储" value="LOW" />
          <el-option label="超高储" value="HIGH" />
        </el-select>
        <el-button size="small" @click="loadReport">刷新</el-button>
        <span class="toolbar-tip">共 {{ reportData.length }} 条记录</span>
      </div>

      <!-- 表格 -->
      <el-table :data="reportData" stripe size="small" v-loading="loading"
        :row-class-name="rowClass" style="width: 100%">
        <el-table-column prop="materialCode" label="物料号" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" />
        <el-table-column prop="stockQty" label="当前库存" width="100" align="right" />
        <el-table-column label="低储阈值" width="100" align="center">
          <template #default="{ row }">{{ row.minStockDays }} 天</template>
        </el-table-column>
        <el-table-column label="高储阈值" width="100" align="center">
          <template #default="{ row }">{{ row.maxStockDays }} 天</template>
        </el-table-column>
        <el-table-column label="内置评级" width="100" align="center">
          <template #default="{ row }">
            <span class="badge" :class="'badge-' + badgeClass(row.ruleEvaluation)">
              {{ ruleLabel(row.ruleEvaluation) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small"
              :loading="predicting === row.materialCode"
              @click="handlePredict(row)">
              AI 深度推演
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
/**
 * 库存报表 — 筛选 + 颜色高亮 + AI 推演触发。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getStockReport } from '@/api/stock'
import { triggerPredict } from '@/api/ai'

const reportData = ref([])
const loading = ref(false)
const predicting = ref(null)

const filterCode = ref('')
const filterStatus = ref('')

onMounted(() => loadReport())

async function loadReport() {
  loading.value = true
  try {
    const data = await getStockReport({
      materialCode: filterCode.value || undefined,
      alarmStatus: filterStatus.value || undefined
    })
    reportData.value = data || []
  } catch { /* */ } finally {
    loading.value = false
  }
}

async function handlePredict(row) {
  predicting.value = row.materialCode
  try {
    await triggerPredict(row.materialCode)
    ElMessage.success(`物料 ${row.materialCode} AI 预测已启动`)
  } catch {
    ElMessage.error('启动失败，请稍后重试')
  } finally {
    predicting.value = null
  }
}

function rowClass({ row }) {
  if (row.ruleEvaluation === 'LOW_STOCK') return 'low-stock-row'
  if (row.ruleEvaluation === 'HIGH' || row.ruleEvaluation === 'DEAD_STOCK') return 'high-stock-row'
  return ''
}

function badgeClass(v) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'warn', 'HIGH': 'warn', 'NORMAL': 'success' }
  return m[v] || 'default'
}
function ruleLabel(v) {
  const m = { 'LOW_STOCK': '超低储', 'DEAD_STOCK': '滞销', 'HIGH': '超高储', 'NORMAL': '正常' }
  return m[v] || v
}
</script>

<style scoped>
.toolbar-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}
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
</style>
