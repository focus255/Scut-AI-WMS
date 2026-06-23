<!--
  出入库历史查询页 — 标签卡切换入库历史/出库历史。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ========== 入库历史 ========== -->
        <el-tab-pane label="入库历史" name="inbound">
          <div class="history-stats">
            <span>总批次数 <b>{{ inboundSummary.totalBatches }}</b></span>
            <span>总入库量 <b>{{ inboundSummary.totalQty }}</b> 件</span>
          </div>
          <div class="toolbar">
            <el-date-picker v-model="inboundDateRange" type="daterange" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" size="small"
              format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              style="width: 260px" @change="loadInboundHistory" />
            <el-select v-model="inboundStatus" placeholder="状态" size="small" clearable
              style="width: 110px" @change="loadInboundHistory">
              <el-option label="已完成" value="已完成" />
              <el-option label="未入库" value="未入库" />
            </el-select>
            <el-input v-model="inboundKeyword" placeholder="单号/供应商" size="small" clearable
              style="width: 160px" @keyup.enter="loadInboundHistory" />
            <el-button size="small" @click="loadInboundHistory">查询</el-button>
          </div>
          <el-table :data="inboundList" stripe size="small" v-loading="inboundLoading"
            empty-text="暂无入库记录">
            <el-table-column prop="orderNo" label="入库单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="supplierCode" label="供应商" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination v-if="inboundTotal > inboundSize"
              :current-page="inboundPage" :page-size="inboundSize" :total="inboundTotal"
              layout="total, prev, pager, next" size="small"
              @current-change="(p) => { inboundPage = p; loadInboundHistory() }" />
          </div>
        </el-tab-pane>

        <!-- ========== 出库历史 ========== -->
        <el-tab-pane label="出库历史" name="outbound">
          <div class="history-stats">
            <span>总批次数 <b>{{ outboundSummary.totalBatches }}</b></span>
            <span>总出库量 <b>{{ outboundSummary.totalQty }}</b> 件</span>
          </div>
          <div class="toolbar">
            <el-date-picker v-model="outboundDateRange" type="daterange" range-separator="至"
              start-placeholder="开始日期" end-placeholder="结束日期" size="small"
              format="YYYY-MM-DD" value-format="YYYY-MM-DD"
              style="width: 260px" @change="loadOutboundHistory" />
            <el-select v-model="outboundStatus" placeholder="状态" size="small" clearable
              style="width: 110px" @change="loadOutboundHistory">
              <el-option label="已完成" value="已完成" />
              <el-option label="未出库" value="未出库" />
              <el-option label="部分出库" value="部分出库" />
            </el-select>
            <el-input v-model="outboundKeyword" placeholder="单号" size="small" clearable
              style="width: 160px" @keyup.enter="loadOutboundHistory" />
            <el-button size="small" @click="loadOutboundHistory">查询</el-button>
          </div>
          <el-table :data="outboundList" stripe size="small" v-loading="outboundLoading"
            empty-text="暂无出库记录">
            <el-table-column prop="orderNo" label="出库单号" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="badge" :class="outStatusClass(row.status)">{{ row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination v-if="outboundTotal > outboundSize"
              :current-page="outboundPage" :page-size="outboundSize" :total="outboundTotal"
              layout="total, prev, pager, next" size="small"
              @current-change="(p) => { outboundPage = p; loadOutboundHistory() }" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getInboundOrders } from '@/api/inbound'
import { getOutboundOrders } from '@/api/outbound'

const activeTab = ref('inbound')

// ========== 入库历史 ==========
const inboundList = ref([])
const inboundLoading = ref(false)
const inboundPage = ref(1), inboundSize = ref(10), inboundTotal = ref(0)
const inboundDateRange = ref(null), inboundStatus = ref(''), inboundKeyword = ref('')
const inboundSummary = reactive({ totalBatches: 0, totalQty: 0 })

async function loadInboundHistory() {
  inboundLoading.value = true
  try {
    const params = { page: inboundPage.value, size: inboundSize.value }
    if (inboundDateRange.value) {
      params.startDate = inboundDateRange.value[0]
      params.endDate = inboundDateRange.value[1]
    }
    if (inboundStatus.value) params.status = inboundStatus.value
    if (inboundKeyword.value.trim()) params.keyword = inboundKeyword.value.trim()
    const data = await getInboundOrders(params)
    inboundList.value = data.records || []
    inboundTotal.value = data.total || 0
    inboundSummary.totalBatches = inboundTotal.value
    inboundSummary.totalQty = (data.records || []).reduce((s, r) => s + (r.actualQty || 0), 0)
  } catch { inboundList.value = [] }
  finally { inboundLoading.value = false }
}

// ========== 出库历史 ==========
const outboundList = ref([])
const outboundLoading = ref(false)
const outboundPage = ref(1), outboundSize = ref(10), outboundTotal = ref(0)
const outboundDateRange = ref(null), outboundStatus = ref(''), outboundKeyword = ref('')
const outboundSummary = reactive({ totalBatches: 0, totalQty: 0 })

function outStatusClass(s) {
  if (s === '已完成') return 'badge-success'
  if (s === '部分出库') return 'badge-warn'
  return 'badge-default'
}

async function loadOutboundHistory() {
  outboundLoading.value = true
  try {
    const params = { page: outboundPage.value, size: outboundSize.value }
    if (outboundDateRange.value) {
      params.startDate = outboundDateRange.value[0]
      params.endDate = outboundDateRange.value[1]
    }
    if (outboundStatus.value) params.status = outboundStatus.value
    if (outboundKeyword.value.trim()) params.orderNo = outboundKeyword.value.trim()
    const data = await getOutboundOrders(params)
    outboundList.value = data.records || []
    outboundTotal.value = data.total || 0
    outboundSummary.totalBatches = outboundTotal.value
    outboundSummary.totalQty = (data.records || []).reduce((s, r) => s + (r.actualQty || 0), 0)
  } catch { outboundList.value = [] }
  finally { outboundLoading.value = false }
}

function onTabChange(tab) {
  if (tab === 'inbound') loadInboundHistory()
  else loadOutboundHistory()
}

onMounted(() => loadInboundHistory())
</script>

<style scoped>
.history-stats {
  display: flex; gap: 24px; padding: 10px 16px; margin-bottom: 14px;
  background: #f7f9fc; border: 1px solid var(--border-light); border-radius: 4px;
  font-size: 13px; color: var(--text-secondary);
}
.history-stats b { color: var(--wms-primary); margin-left: 4px; }

.badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 12px; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-default { background: #f4f4f5; color: #909399; }
</style>
