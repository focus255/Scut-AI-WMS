<!--
  库存追溯查询页 — 按物料/条码/入库单号查询条码生命周期轨迹。
  @author Focus
  @date 2026-06-10
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <div class="block-header">
        <span class="block-title">库存追溯查询</span>
        <span class="toolbar-tip">按物料编码、条码号或入库单号查询条码生命周期轨迹</span>
      </div>
      <div class="toolbar">
        <el-input v-model="query.materialCode" placeholder="物料编码" clearable size="small"
          style="width: 160px" @keyup.enter="doQuery" />
        <el-input v-model="query.barcode" placeholder="条码号（模糊）" clearable size="small"
          style="width: 200px" @keyup.enter="doQuery" />
        <el-input v-model="query.orderNo" placeholder="入库单号（模糊）" clearable size="small"
          style="width: 200px" @keyup.enter="doQuery" />
        <el-button type="primary" size="small" :loading="loading" @click="doQuery">
          查询
        </el-button>
        <span class="toolbar-tip">{{ traceData.length > 0 ? `共 ${traceData.length} 条追溯记录` : '至少输入一个查询条件' }}</span>
      </div>
      <el-table :data="traceData" stripe size="small" v-loading="loading"
        empty-text="暂无追溯数据，请尝试修改查询条件">
        <el-table-column prop="barcode" label="条码号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料号" width="130" />
        <el-table-column prop="supplierCode" label="供应商" width="150" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="入库单号" width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <span class="badge" :class="statusBadgeClass(row.status)">
              {{ row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="箱容量" width="80" align="right">
          <template #default="{ row }">{{ row.packCapacity || '—' }}</template>
        </el-table-column>
        <el-table-column label="计划数" width="80" align="right">
          <template #default="{ row }">{{ row.planQty || '—' }}</template>
        </el-table-column>
        <el-table-column label="实收数" width="80" align="right">
          <template #default="{ row }">{{ row.actualQty || '—' }}</template>
        </el-table-column>
        <el-table-column prop="inboundCreatedAt" label="入库时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.inboundCreatedAt || '—' }}</template>
        </el-table-column>
        <el-table-column prop="barcodeUpdatedAt" label="最后更新" width="170" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup>
/**
 * 库存追溯查询 — 联查条码与入库明细，展示完整生命周期。
 */
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventoryTrace } from '@/api/inbound'

const loading = ref(false)
const traceData = ref([])

const query = reactive({
  materialCode: '',
  barcode: '',
  orderNo: ''
})

async function doQuery() {
  const hasMaterial = query.materialCode.trim()
  const hasBarcode = query.barcode.trim()
  const hasOrderNo = query.orderNo.trim()
  if (!hasMaterial && !hasBarcode && !hasOrderNo) {
    ElMessage.warning('请至少输入一个查询条件')
    return
  }
  loading.value = true
  try {
    const data = await getInventoryTrace({
      materialCode: hasMaterial || undefined,
      barcode: hasBarcode || undefined,
      orderNo: hasOrderNo || undefined
    })
    traceData.value = data.items || []
  } catch {
    traceData.value = []
  } finally {
    loading.value = false
  }
}

function statusBadgeClass(status) {
  if (status === '在库') return 'badge-success'
  if (status === '已出库') return 'badge-default'
  return 'badge-warn'
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
  white-space: nowrap;
}
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-default { background: #f4f4f5; color: #909399; }
</style>
