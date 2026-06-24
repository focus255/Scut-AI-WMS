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
        <el-input v-model="queryCode" placeholder="输入物料号查询最新 AI 报告" clearable
          size="small" style="width: 300px" @keyup.enter="handleQuery" />
        <el-button type="primary" size="small" :loading="queryLoading" @click="handleQuery">
          查询报告
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
            <span class="info-label">置信度</span>
            <span class="info-value">{{ (report.confidence * 100).toFixed(0) }}%</span>
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
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getLatestReport, triggerPredict } from '@/api/ai'

const router = useRouter()

const queryCode = ref('')
const queryLoading = ref(false)
const report = ref(null)
const queried = ref(false)

const showConvert = computed(() => {
  if (!report.value) return false
  return report.value.suggestedQty > 0 &&
    (report.value.riskType === 'LOW_STOCK' || report.value.riskType === 'BOTH')
})

async function handleQuery() {
  if (!queryCode.value.trim()) return
  queryLoading.value = true
  queried.value = true
  try {
    report.value = await getLatestReport(queryCode.value.trim())
  } catch {
    report.value = null
    ElMessage.error('查询AI报告失败')
  } finally {
    queryLoading.value = false
  }
}

async function handleTrigger() {
  try {
    await triggerPredict(queryCode.value.trim())
    ElMessage.success('AI 预测任务已启动，请稍后刷新查看')
  } catch {
    ElMessage.error('启动失败')
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
  const m = { 'PENDING': 'default', 'RUNNING': 'warn', 'SUCCESS': 'success', 'MOCKED': 'warn' }
  return m[s] || 'default'
}
function statusLabel(s) {
  const m = { 'PENDING': '等待分析', 'RUNNING': '分析中', 'SUCCESS': '分析完成', 'MOCKED': '降级报告' }
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
