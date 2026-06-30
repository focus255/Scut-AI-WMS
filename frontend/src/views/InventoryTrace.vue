<!--
  库存与看板监控页 — 按物料/二维码/入库单号查询二维码生命周期轨迹。
  @author Focus
  @date 2026-06-10
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <div class="block-header">
        <span class="block-title">库存与看板监控</span>
        <span class="toolbar-tip">按物料号、看板号或入库单号查询二维码生命周期轨迹</span>
      </div>
      <div class="toolbar">
        <el-select v-model="query.materialCode" placeholder="选择物料号" filterable clearable
          size="small" style="width: 180px" :filter-method="filterMaterial">
          <el-option v-for="m in materialOptions" :key="m.materialCode"
            :label="m.materialCode + ' ' + m.materialName" :value="m.materialCode" />
        </el-select>
        <el-input v-model="query.barcode" placeholder="看板号（模糊）" clearable size="small"
          style="width: 200px" @keyup.enter="doQuery" />
        <el-input v-model="query.orderNo" placeholder="入库单号（模糊）" clearable size="small"
          style="width: 200px" @keyup.enter="doQuery" />
        <el-button type="primary" size="small" :loading="loading" @click="doQuery">
          查询
        </el-button>
        <span class="toolbar-tip">共 {{ traceTotal }} 条，第 {{ (tracePage-1)*tracePageSize+1 }}-{{ Math.min(tracePage*tracePageSize, traceTotal) }} 条</span>
      </div>
      <!-- 状态统计 -->
      <div v-if="traceData.length" class="stat-row" style="margin-bottom: 16px">
        <div class="stat-item"><span class="stat-num warn">{{ statusStats['待入库'] }}</span><span class="stat-lbl">待入库</span></div>
        <div class="stat-item"><span class="stat-num success">{{ statusStats['在库'] }}</span><span class="stat-lbl">在库</span></div>
        <div class="stat-item"><span class="stat-num info">{{ statusStats['待出库'] }}</span><span class="stat-lbl">待出库</span></div>
        <div class="stat-item"><span class="stat-num default">{{ statusStats['已出库'] }}</span><span class="stat-lbl">已出库</span></div>
        <div class="stat-item"><span class="stat-num danger">{{ statusStats['封存'] }}</span><span class="stat-lbl">封存</span></div>
        <div class="stat-spacer"></div>
        <div>
          <el-button size="small" :disabled="!selectedRows.length" @click="batchPrint">批量打印 ({{ selectedRows.length }})</el-button>
          <el-button size="small" @click="doExport">导出 CSV</el-button>
        </div>
      </div>

      <el-table :data="traceData" stripe size="small" v-loading="loading"
        empty-text="暂无追溯数据"
        @selection-change="(rows) => selectedRows = rows">
        <el-table-column type="selection" width="40" />
        <el-table-column label="看板号" min-width="280">
          <template #default="{ row }">
            <div class="trace-barcode-cell">
              <QRCode :value="row.barcode" :height="32" :display-value="false" />
              <span class="trace-barcode-text">{{ row.barcode }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料号" width="130" />
        <el-table-column prop="supplierCode" label="供应商" width="150" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="入库单号" width="200" show-overflow-tooltip />
        <el-table-column prop="outboundOrderNo" label="出库单号" width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.outboundOrderNo || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" size="small" disable-transitions>
              {{ row.status }}
            </el-tag>
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
        <el-table-column prop="outboundAt" label="出库时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.outboundAt || '—' }}</template>
        </el-table-column>
        <el-table-column prop="barcodeUpdatedAt" label="最后更新" width="170" show-overflow-tooltip />
      </el-table>
      <div v-if="traceTotal > tracePageSize" style="margin-top:12px;display:flex;justify-content:center">
        <el-pagination v-model:current-page="tracePage" v-model:page-size="tracePageSize"
          :page-sizes="[20,50,100]" :total="traceTotal" layout="total,sizes,prev,pager,next"
          small @current-change="doQuery" @size-change="doQuery" />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 库存与看板监控 — 联查二维码与入库明细，展示完整生命周期。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventoryTrace } from '@/api/inbound'
import { getMaterials } from '@/api/materials'
import { exportCSV } from '@/utils/export'
import QRCode from '@/components/QRCode.vue'

const allMaterials = ref([])
const materialOptions = ref([])
const loading = ref(false)
const traceData = ref([])
const traceTotal = ref(0)
const tracePage = ref(1)
const tracePageSize = ref(20)
const selectedRows = ref([])

const query = reactive({
  materialCode: '',
  barcode: '',
  orderNo: ''
})

// 状态统计（来自后端全局统计，不受分页影响）
const statusStats = ref({ '待入库': 0, '在库': 0, '待出库': 0, '已出库': 0, '封存': 0 })

onMounted(async () => {
  try {
    const data = await getMaterials({ page: 1, size: 100 })
    allMaterials.value = data.records || []
    materialOptions.value = [...allMaterials.value]
  } catch { /* */ }
  // 页面加载即展示全部追溯记录
  doQuery()
})

function filterMaterial(query) {
  if (!query) { materialOptions.value = [...allMaterials.value]; return }
  const q = query.toLowerCase()
  materialOptions.value = allMaterials.value.filter(m =>
    m.materialCode.toLowerCase().includes(q) ||
    (m.materialName && m.materialName.toLowerCase().includes(q)))
}

async function doQuery() {
  loading.value = true
  try {
    const hasMaterial = query.materialCode?.trim()
    const hasBarcode = query.barcode?.trim()
    const hasOrderNo = query.orderNo?.trim()
    const data = await getInventoryTrace({
      materialCode: hasMaterial || undefined,
      barcode: hasBarcode || undefined,
      orderNo: hasOrderNo || undefined,
      page: tracePage.value,
      size: tracePageSize.value
    })
    traceData.value = data.items || []
    traceTotal.value = data.totalCount || 0
    // 使用后端返回的全局状态统计（不受分页影响）
    if (data.statusStats) {
      const ss = data.statusStats
      statusStats.value = {
        '待入库': ss['待入库'] || 0,
        '在库': ss['在库'] || 0,
        '待出库': ss['待出库'] || 0,
        '已出库': ss['已出库'] || 0,
        '封存': (ss['FROZEN'] || 0)
      }
    }
  } catch {
    traceData.value = []
    ElMessage.error('追溯查询失败')
  } finally {
    loading.value = false
  }
}

/**
 * 将二维码状态映射为 Element Plus el-tag 的 type 属性。
 * 五种状态对应五个语义色：待入库=warning 待出库=primary 在库=success 已出库=info 封存=danger
 */
function statusTagType(status) {
  if (status === '在库') return 'success'
  if (status === '待出库') return 'primary'
  if (status === '已出库') return 'info'
  if (status === 'FROZEN' || status === '封存') return 'danger'
  return 'warning' // 待入库 和其他未知状态
}

/**
 * 批量打印选中的二维码标签。
 */
function batchPrint() {
  if (!selectedRows.value.length) { ElMessage.warning('请先选择二维码'); return }
  const barcodes = selectedRows.value.map(r => r.barcode).join('\n')
  const win = window.open('', '_blank', 'width=800,height=600')
  win.document.write('<html><head><title>批量二维码打印</title><\/head><body>' +
    '<pre style="font-family: monospace; font-size: 12px; line-height: 1.8">' + barcodes + '<\/pre>' +
    '<script>window.onload=function(){window.print()}<\/script><\/body><\/html>')
  win.document.close()
}

function doExport() {
  if (!traceData.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'barcode', label: '看板号' },
    { key: 'materialCode', label: '物料号' },
    { key: 'supplierCode', label: '供应商' },
    { key: 'orderNo', label: '入库单号' },
    { key: 'status', label: '状态' },
    { key: 'actualQty', label: '实收数' },
    { key: 'barcodeUpdatedAt', label: '最后更新' }
  ], traceData.value, `库存与看板监控_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
</script>

<style scoped>
.stat-row { display: flex; align-items: center; gap: 24px; background: var(--content-bg); padding: 14px 20px; border-radius: 4px; box-shadow: var(--shadow-light); }
.stat-item { display: flex; flex-direction: column; align-items: center; }
.stat-num { font-size: 22px; font-weight: 700; line-height: 1.2; }
.stat-num.success { color: #67c23a; }
.stat-num.warn { color: #e6a23c; }
.stat-num.danger { color: #f56c6c; }
.stat-num.default { color: #909399; }
.stat-num.info { color: #409eff; }
.stat-lbl { font-size: 12px; color: var(--text-secondary); }
.stat-spacer { flex: 1; }
.toolbar-tip {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}
.trace-barcode-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 4px 0;
}
.trace-barcode-cell :deep(canvas) {
  max-width: 200px;
}
.trace-barcode-text {
  font-size: 11px;
  color: var(--text-secondary);
  word-break: break-all;
}

@media (max-width: 768px) {
  .stat-row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 14px;
  }

  .stat-spacer,
  .toolbar-tip {
    display: none;
  }

  .trace-barcode-cell :deep(canvas) {
    max-width: 150px;
  }
}
</style>
