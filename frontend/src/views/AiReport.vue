<!--
  AI 风险预测与智能报告详页。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <!-- 查询栏 -->
    <div class="content-block" style="margin-bottom: 16px">
      <div class="toolbar" style="margin-bottom: 0">
        <el-select v-model="queryCode" placeholder="选择或搜索物料号" filterable clearable
          size="small" style="width: 300px" :filter-method="filterMaterial" @change="handleQuery">
          <el-option v-for="m in materialOptions" :key="m.materialCode"
            :label="m.materialCode + ' — ' + m.materialName" :value="m.materialCode" />
        </el-select>
        <el-button type="primary" size="small" :loading="queryLoading"
          style="min-width: 90px" @click="handleQuery">
          查询报告
        </el-button>
        <el-button size="small" :disabled="!queryCode.trim()" :loading="regenerating"
          style="min-width: 90px" @click="handleRegenerate">
          重新生成
        </el-button>
      </div>
    </div>

    <template v-if="report">
      <!-- 基本属性 -->
      <div class="content-block">
        <div class="block-header">
          <span class="block-title">基本属性</span>
          <span class="badge" :class="'badge-' + statusBadge(report.predictionStatus)">
            {{ statusLabel(report.predictionStatus) }}
          </span>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">报告编号</span>
            <span class="info-value">{{ report.id }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">物料号</span>
            <span class="info-value">{{ report.materialCode }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">快照库存</span>
            <span class="info-value">{{ report.currentStock }} 件</span>
          </div>
          <div class="info-item">
            <span class="info-label">诊断时间</span>
            <span class="info-value">{{ report.createdAt }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">诊断模型</span>
            <span class="info-value">{{ report.model || '—' }}</span>
          </div>
        </div>
      </div>

      <!-- AI 核心诊断 -->
      <div class="content-block">
        <div class="block-header">
          <span class="block-title">AI 核心诊断</span>
        </div>
        <div class="diagnosis-summary">
          <div class="ds-item">
            <span class="ds-label">风险判定</span>
            <span class="badge badge-lg" :class="'badge-' + riskBadge(report.riskType)">
              {{ riskLabel(report.riskType) }}
            </span>
          </div>
          <div class="ds-item">
            <span class="ds-label">风险等级</span>
            <span class="badge badge-lg" :class="'badge-' + levelBadge(report.riskLevel)">
              {{ report.riskLevel }}
            </span>
          </div>
        </div>
        <div class="analysis-box">
          <h4>根因分析</h4>
          <p>{{ report.analysisContent }}</p>
        </div>
      </div>

      <!-- 行动建议 -->
      <div class="content-block">
        <div class="block-header">
          <span class="block-title">精益行动建议</span>
          <el-button v-if="showConvert" type="primary" size="small"
            @click="handleConvert">
            采纳建议 → 一键转入库单
          </el-button>
        </div>
        <p class="advice-text">{{ report.replenishmentSuggestion }}</p>
        <div v-if="report.suggestedQty > 0" class="advice-qty">
          建议补货量：<strong>{{ report.suggestedQty }}</strong> 件
        </div>
      </div>
    </template>

    <div v-else-if="queried" class="content-block">
      <div class="empty-hint">
        <p>未找到该物料的 AI 分析报告</p>
        <el-button text type="primary" size="small" @click="handleTrigger">
          点击发起 AI 预测
        </el-button>
      </div>
    </div>
    <div v-else class="content-block">
      <div class="empty-hint">输入物料号查询 AI 分析报告</div>
    </div>
  </div>
</template>

<script setup>
/**
 * AI 报告详页。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLatestReport, triggerPredict } from '@/api/ai'
import { getMaterials } from '@/api/materials'

const router = useRouter()

const allMaterials = ref([])
const materialOptions = ref([])
const queryCode = ref('')
const queryLoading = ref(false)
const regenerating = ref(false)
const report = ref(null)
const queried = ref(false)

onMounted(async () => {
  try {
    const data = await getMaterials({ page: 1, size: 100 })
    allMaterials.value = data.records || []
    materialOptions.value = [...allMaterials.value]
  } catch { /* */ }
})

/** el-select 自定义过滤：按物料号或名称匹配 */
function filterMaterial(query) {
  if (!query) {
    materialOptions.value = [...allMaterials.value]
    return
  }
  const q = query.toLowerCase()
  materialOptions.value = allMaterials.value.filter(m =>
    m.materialCode.toLowerCase().includes(q) ||
    (m.materialName && m.materialName.toLowerCase().includes(q))
  )
}

const showConvert = computed(() => {
  if (!report.value) return false
  return report.value.suggestedQty > 0 &&
    (report.value.riskType === 'LOW_STOCK' || report.value.riskType === 'BOTH')
})

async function handleQuery() {
  if (!queryCode.value.trim()) return
  queryLoading.value = true
  queried.value = true
  const code = queryCode.value.trim()
  try {
    report.value = await getLatestReport(code)
  } catch {
    report.value = null
  }

  // 报告不存在或失败 → 自动生成
  if (!report.value || report.value.predictionStatus === 'FAILED') {
    report.value = null
    ElMessage.info('正在生成报告，请稍候...')
    try { await triggerPredict(code) } catch { /* 忽略 */ }

    // 轮询等待生成完成，最多等 20 秒
    for (let i = 0; i < 10; i++) {
      await new Promise(r => setTimeout(r, 2000))
      try {
        const r = await getLatestReport(code)
        if (r && (r.predictionStatus === 'SUCCESS')) {
          report.value = r
          ElMessage.success('报告生成完成')
          break
        }
      } catch { /* 继续等待 */ }
    }
    if (!report.value) {
      ElMessage.warning('报告生成超时，请稍后手动刷新')
    }
  }
  queryLoading.value = false
}

async function handleTrigger() {
  try {
    await triggerPredict(queryCode.value.trim())
    ElMessage.success('AI 预测任务已启动，请稍后刷新查看')
  } catch {
    ElMessage.error('启动失败')
  }
}

async function handleRegenerate() {
  if (!queryCode.value.trim()) return
  regenerating.value = true
  try {
    await triggerPredict(queryCode.value.trim())
    ElMessage.success('已提交重新生成，请等待几秒后查询')
    // 等3秒后自动查询
    setTimeout(() => handleQuery(), 4000)
  } catch (err) {
    ElMessage.error(err?.message || '重新生成失败')
  } finally {
    regenerating.value = false
  }
}

function handleConvert() {
  ElMessage.success('正在跳转入库单页，AI 建议数据已自动填充')
  router.push({
    path: '/inbound-outbound',
    query: { materialCode: report.value.materialCode, suggestedQty: report.value.suggestedQty }
  })
}

// ---- 标签 ----
function statusBadge(s) {
  const m = { 'PENDING': 'default', 'RUNNING': 'warn', 'SUCCESS': 'success', 'FAILED': 'danger', 'MOCKED': 'warn' }
  return m[s] || 'default'
}
function statusLabel(s) {
  const m = { 'PENDING': '等待分析', 'RUNNING': '分析中', 'SUCCESS': '分析完成', 'FAILED': '分析失败', 'MOCKED': '降级报告' }
  return m[s] || s
}
function riskBadge(r) {
  const m = { 'LOW_STOCK': 'danger', 'DEAD_STOCK': 'warn', 'BOTH': 'danger', 'NORMAL': 'success' }
  return m[r] || 'default'
}
function riskLabel(r) {
  const m = { 'LOW_STOCK': '⚠ 断供预警', 'DEAD_STOCK': '📉 呆滞风险', 'BOTH': '🚨 双重风险', 'NORMAL': '✅ 正常' }
  return m[r] || r
}
function levelBadge(l) {
  const m = { 'CRITICAL': 'danger', 'HIGH': 'warn', 'MEDIUM': 'default', 'LOW': 'success' }
  return m[l] || 'default'
}
function sourceLabel(s) {
  const m = { 'SUCCESS': 'AI 大模型', 'FAILED': '调用失败', 'MOCKED': '本地规则引擎', 'PENDING': '等待分析', 'RUNNING': '分析中' }
  return m[s] || s
}
</script>

<style scoped>
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
}
.info-label { font-size: 12px; color: var(--text-secondary); }
.info-value { font-size: 14px; color: var(--text-primary); font-weight: 500; }

.diagnosis-summary {
  display: flex;
  gap: 32px;
  margin-bottom: 20px;
}
.ds-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ds-label { font-size: 12px; color: var(--text-secondary); }

.analysis-box {
  background: #fafafa;
  border-radius: 4px;
  padding: 16px 20px;
}
.analysis-box h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}
.analysis-box p {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-regular);
}

.advice-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-regular);
  margin-bottom: 16px;
}
.advice-qty {
  padding: 10px 16px;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 15px;
  color: var(--wms-primary);
}
.advice-qty strong {
  font-size: 20px;
}

/* 徽章 */
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
}
.badge-lg { padding: 4px 12px; font-size: 14px; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-danger  { background: #fef0f0; color: #f56c6c; }
.badge-default { background: #f4f4f5; color: #909399; }

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .diagnosis-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .analysis-box {
    padding: 14px;
  }
}

@media (max-width: 420px) {
  .diagnosis-summary {
    grid-template-columns: 1fr;
  }
}
</style>
