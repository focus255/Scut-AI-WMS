<!--
  手机端扫码操作记录页。
  @author Focus
  @date 2026-06-24
-->
<template>
  <div class="history-page">
    <div class="section-title">今日操作记录</div>

    <div v-if="records.length === 0 && !loading" class="empty">暂无记录</div>

    <div v-for="(r, i) in records" :key="i" class="record-card" :class="'card-' + r.type">
      <div class="card-top">
        <span class="card-badge" :class="'badge-' + r.type">{{ typeLabel(r.type) }}</span>
        <span class="card-time">{{ r.time }}</span>
      </div>
      <div class="card-body">
        <span v-if="r.type === 'inbound'">供应商：{{ r.materialCode?.replace('供应商: ', '') }}</span>
        <span v-else>物料：{{ r.materialCode }}</span>
        <span v-if="r.qty">数量：{{ r.qty }} 件</span>
      </div>
      <div class="card-barcode">{{ r.type === 'inbound' ? '单号：' : '' }}{{ r.barcode }}</div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const records = ref([])
const loading = ref(true)

function typeLabel(t) {
  return { inbound: '入库', outbound: '出库', seal: '封存', unseal: '解封' }[t] || t
}

async function loadRecords() {
  loading.value = true
  try {
    const today = new Date().toISOString().substring(0, 10)
    const results = []

    // 入库记录
    try {
      const ib = await request.get('/inbound/orders', { params: { page: 1, size: 30 } })
      ;(ib.records || []).forEach(r => {
        if (r.createdAt?.startsWith(today)) {
          results.push({ type: 'inbound', materialCode: '供应商: ' + (r.supplierCode || '—'), qty: 0, barcode: r.orderNo, time: r.createdAt?.substring(11, 19) || '', ts: r.createdAt })
        }
      })
    } catch { /* */ }

    // 出库历史流水（含物料号、条码、数量）
    try {
      const ob = await request.get('/outbound/histories', { params: { page: 1, size: 50 } })
      ;(ob.records || []).forEach(r => {
        if (r.createdAt?.startsWith(today)) {
          results.push({ type: 'outbound', materialCode: r.materialCode, qty: r.deductQty, barcode: r.barcode, time: r.createdAt?.substring(11, 19) || '', ts: r.createdAt })
        }
      })
    } catch { /* */ }

    // 封存记录（含物料号、条码）
    try {
      const fz = await request.get('/freeze/list', { params: { page: 1, size: 30 } })
      ;(fz.records || []).forEach(r => {
        if (r.freezeTime?.startsWith(today)) {
          results.push({ type: 'seal', materialCode: r.materialCode, qty: 0, barcode: r.barcode, time: r.freezeTime?.substring(11, 19) || '', ts: r.freezeTime })
        }
        if (r.unfreezeTime?.startsWith(today)) {
          results.push({ type: 'unseal', materialCode: r.materialCode, qty: 0, barcode: r.barcode, time: r.unfreezeTime?.substring(11, 19) || '', ts: r.unfreezeTime })
        }
      })
    } catch { /* */ }

    results.sort((a, b) => (b.ts || '').localeCompare(a.ts || ''))
    records.value = results.slice(0, 50)
  } catch { records.value = [] }
  finally { loading.value = false }
}

onMounted(() => loadRecords())
</script>

<style scoped>
.section-title { font-size: 15px; font-weight: 600; color: #303133; margin-bottom: 12px; }
.empty { text-align: center; padding: 60px 0; color: #999; font-size: 14px; }
.loading { text-align: center; padding: 20px; color: #999; }

.record-card {
  background: #fff; border-radius: 8px; padding: 12px; margin-bottom: 8px;
  border-left: 4px solid #ccc; box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.card-inbound { border-left-color: #67c23a; }
.card-outbound { border-left-color: #909399; }
.card-seal { border-left-color: #e6a23c; }
.card-unseal { border-left-color: #409eff; }

.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.card-badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 11px; font-weight: 500; color: #fff; }
.badge-inbound { background: #67c23a; }
.badge-outbound { background: #909399; }
.badge-seal { background: #e6a23c; }
.badge-unseal { background: #409eff; }
.card-time { font-size: 12px; color: #999; }

.card-body { display: flex; gap: 16px; font-size: 13px; color: #606266; margin-bottom: 4px; }
.card-barcode { font-size: 11px; color: #bbb; word-break: break-all; font-family: monospace; }
</style>
