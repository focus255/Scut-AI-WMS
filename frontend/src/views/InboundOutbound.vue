<!--
  入库与出库管理页。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab">
        <!-- === 入库管理 === -->
        <el-tab-pane label="入库管理" name="inbound">
          <div class="inbound-summary">
            <div>
              <div class="summary-title">入库单流转</div>
              <div class="summary-desc">创建入库单后确认到货，系统会生成条码并更新库存。</div>
            </div>
            <div class="summary-stats">
              <span>待入库 {{ pendingCount }}</span>
              <span>已完成 {{ completedCount }}</span>
            </div>
          </div>

          <div class="toolbar inbound-toolbar">
            <el-button type="primary" size="small" @click="openInboundDialog">
              <el-icon :size="14"><Plus /></el-icon>
              <span>新建入库单</span>
            </el-button>
            <el-button size="small" :loading="inboundLoading" @click="loadOrders">刷新</el-button>
          </div>
          <el-table :data="inboundList" stripe size="small" v-loading="inboundLoading"
            empty-text="暂无入库单数据" @row-click="openDetailDialog" style="cursor: pointer">
            <el-table-column prop="orderNo" label="入库单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="supplierCode" label="供应商" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="260" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="primary" link size="small"
                  @click.stop="openEditDialog(row)">
                  编辑
                </el-button>
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click.stop="openConfirmDialog(row)">
                  确认入库
                </el-button>
                <el-button link size="small" @click.stop="openPrintDialog(row)">
                  <el-icon :size="14"><Printer /></el-icon>打印
                </el-button>
                <span v-if="row.status === '已完成'" class="muted-text">无需操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- === 出库管理 === -->
        <el-tab-pane label="出库管理" name="outbound">
          <div class="empty-hint">出库管理功能开发中</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新建入库单对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="dialogVisible" title="新建入库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close class="inbound-dialog">
        <el-alert v-if="isAiDraft" title="已根据 AI 建议预填物料和计划数量，请选择供应商后保存。"
          type="info" show-icon :closable="false" class="draft-alert" />
        <el-form ref="formRef" :model="inboundForm" :rules="inboundRules" label-width="88px">
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="inboundForm.supplierCode" placeholder="请选择供应商" style="width: 100%"
              filterable>
              <el-option v-for="s in supplierOptions" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span>
                <span>单箱容量</span>
                <span>计划入库数</span>
                <span>操作</span>
              </div>
              <div v-for="(item, idx) in inboundForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                  filterable remote :remote-method="(q) => searchMaterials(q, idx)"
                  :loading="materialSearchLoading[idx]" clearable style="width: 100%"
                  @focus="searchMaterials('', idx)">
                  <el-option v-for="m in materialOptions[idx]" :key="m.materialCode"
                    :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-button type="danger" link size="small" @click="removeDetail(idx)"
                  :disabled="inboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addDetail">
                  <el-icon :size="14"><Plus /></el-icon>
                  <span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">保存后可在列表中执行确认入库。</span>
            <div>
              <el-button @click="dialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleCreate">保存</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 确认入库对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="confirmVisible" title="手工确认入库"
        width="min(680px, calc(100vw - 32px))" destroy-on-close>
        <div class="confirm-info">
          <span class="confirm-label">入库单号：</span>
          <strong>{{ confirmTarget?.orderNo }}</strong>
          <span class="confirm-divider">|</span>
          <span class="confirm-label">供应商：</span>
          <span>{{ confirmTarget?.supplierCode }}</span>
        </div>
        <el-table :data="confirmDetails" stripe size="small" style="margin-top: 12px">
          <el-table-column prop="materialCode" label="物料号" width="140" />
          <el-table-column prop="planQty" label="计划入库数" width="110" align="right" />
          <el-table-column label="实际到货数" min-width="160">
            <template #default="{ row, $index }">
              <el-input-number v-model="row._actualQty" :min="0" :max="999999"
                size="small" controls-position="right" style="width: 140px" />
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">请清点后输入实际到货数量，默认与计划数一致。</span>
            <div>
              <el-button @click="confirmVisible = false">取消</el-button>
              <el-button type="primary" :loading="confirmSubmitting" @click="handleConfirmSubmit">
                确认入库
              </el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 入库单详情对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="detailVisible" title="入库单详情"
        width="min(640px, calc(100vw - 32px))" destroy-on-close>
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
        <div v-else class="empty-hint" style="padding: 30px 0">加载中...</div>
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 修改入库单对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="editVisible" title="修改入库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close>
        <el-alert title="修改后需重新确认入库，原确认信息将被清除。"
          type="warning" show-icon :closable="false" class="draft-alert" />
        <el-form ref="editFormRef" :model="editForm" :rules="inboundRules" label-width="88px">
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="editForm.supplierCode" placeholder="请选择供应商" style="width: 100%" filterable>
              <el-option v-for="s in supplierOptions" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span><span>单箱容量</span><span>计划入库数</span><span>操作</span>
              </div>
              <div v-for="(item, idx) in editForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                  filterable remote :remote-method="(q) => searchEditMaterials(q, idx)"
                  :loading="editMaterialLoading[idx]" clearable style="width: 100%"
                  @focus="searchEditMaterials('', idx)">
                  <el-option v-for="m in editMaterialOptions[idx]" :key="m.materialCode"
                    :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small" controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small" controls-position="right" />
                <el-button type="danger" link size="small" @click="removeEditDetail(idx)"
                  :disabled="editForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon><span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addEditDetail">
                  <el-icon :size="14"><Plus /></el-icon><span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">修改后需重新确认入库。</span>
            <div>
              <el-button @click="editVisible = false">取消</el-button>
              <el-button type="primary" :loading="editSubmitting" @click="handleEditSubmit">保存修改</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 打印入库单对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="printVisible" title="打印预览 — 入库单"
        width="min(700px, calc(100vw - 32px))" destroy-on-close>
        <div class="print-area" id="printInboundArea">
          <div v-if="printOrder" class="print-content">
            <h2>智库 WMS — 入库单</h2>
            <div class="print-meta">
              <span><strong>单号：</strong>{{ printOrder.orderNo }}</span>
              <span><strong>供应商：</strong>{{ printOrder.supplierCode }}</span>
              <span><strong>状态：</strong>{{ printOrder.status }}</span>
              <span><strong>创建时间：</strong>{{ printOrder.createdAt }}</span>
            </div>
            <table class="print-table">
              <thead>
                <tr>
                  <th>序号</th><th>物料号</th><th>单箱容量</th><th>计划数</th><th>实收数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(d, i) in printOrder.details" :key="i">
                  <td>{{ i + 1 }}</td>
                  <td>{{ d.materialCode }}</td>
                  <td>{{ d.packCapacity }}</td>
                  <td>{{ d.planQty }}</td>
                  <td>{{ d.actualQty }}</td>
                </tr>
              </tbody>
            </table>
            <div class="print-footer">
              <span>打印时间：{{ new Date().toLocaleString('zh-CN') }}</span>
              <span>操作员：{{ userStore?.username || '—' }}</span>
            </div>
          </div>
        </div>
        <template #footer>
          <el-button @click="printVisible = false">关闭</el-button>
          <el-button type="primary" @click="doPrint">打印</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 入库与出库管理。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Printer } from '@element-plus/icons-vue'
import { getInboundOrders, createInbound, updateInbound, confirmInbound, getInboundDetail } from '@/api/inbound'
import { getSuppliers } from '@/api/suppliers'
import { getMaterials } from '@/api/materials'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('inbound')

// ==================== 入库列表 ====================
const inboundList = ref([])
const inboundLoading = ref(false)

// ==================== 供应商选项（动态加载） ====================
const supplierOptions = ref([])

// ==================== 新建入库单 ====================
const dialogVisible = ref(false)
const formRef = ref(null)
const isAiDraft = ref(false)

const inboundForm = reactive({
  supplierCode: '',
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})
const inboundRules = {
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

// ==================== 物料搜索下拉 ====================
const materialOptions = ref({})
const materialSearchLoading = ref({})

// ==================== 修改入库单 ====================
const editVisible = ref(false)
const editFormRef = ref(null)
const editTarget = ref(null)
const editSubmitting = ref(false)
const editMaterialOptions = ref({})
const editMaterialLoading = ref({})
const editForm = reactive({
  supplierCode: '',
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})

// ==================== 确认入库 ====================
const confirmVisible = ref(false)
const confirmTarget = ref(null)
const confirmDetails = ref([])
const confirmSubmitting = ref(false)

// ==================== 入库单详情 ====================
const detailVisible = ref(false)
const detailData = ref(null)

// ==================== 打印 ====================
const printVisible = ref(false)
const printOrder = ref(null)

// ==================== 计算属性 ====================
const pendingCount = computed(() => inboundList.value.filter(row => row.status !== '已完成').length)
const completedCount = computed(() => inboundList.value.filter(row => row.status === '已完成').length)

onMounted(() => {
  loadOrders()
  loadSuppliers()
  applyAiInboundDraft()
})

watch(
  () => route.query,
  () => applyAiInboundDraft()
)

// ==================== 加载数据 ====================
async function loadOrders() {
  inboundLoading.value = true
  try {
    const data = await getInboundOrders({ page: 1, size: 50 })
    inboundList.value = data.records || []
  } catch { /* */ } finally {
    inboundLoading.value = false
  }
}

async function loadSuppliers() {
  try {
    const data = await getSuppliers({ page: 1, size: 100 })
    supplierOptions.value = data.records || []
  } catch { /* */ }
}

// ==================== 物料搜索 ====================
async function searchMaterials(query, idx) {
  materialSearchLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    materialOptions.value[idx] = data.records || []
  } catch {
    materialOptions.value[idx] = []
  } finally {
    materialSearchLoading.value[idx] = false
  }
}

// ==================== 新建入库单 ====================
function addDetail() {
  inboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}
function removeDetail(idx) {
  if (inboundForm.details.length > 1) inboundForm.details.splice(idx, 1)
}

function openInboundDialog() {
  isAiDraft.value = false
  inboundForm.supplierCode = ''
  inboundForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
  materialOptions.value = {}
  dialogVisible.value = true
}

function applyAiInboundDraft() {
  const materialCode = String(route.query.materialCode || '').trim()
  const suggestedQty = Number(route.query.suggestedQty || 0)
  if (!materialCode || suggestedQty <= 0) return

  isAiDraft.value = true
  activeTab.value = 'inbound'
  inboundForm.supplierCode = ''
  inboundForm.details = [{
    materialCode,
    packCapacity: 20,
    planQty: suggestedQty
  }]
  materialOptions.value = {}
  dialogVisible.value = true
  ElMessage.info('已根据 AI 建议预填入库明细，请选择供应商后保存')

  router.replace({ path: route.path, query: {} })
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const invalidDetail = inboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.packCapacity || !item.planQty
  )
  if (invalidDetail) {
    ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createInbound({
      supplierCode: inboundForm.supplierCode,
      details: inboundForm.details
    })
    ElMessage.success('入库单创建成功')
    dialogVisible.value = false
    loadOrders()
  } catch { /* */ }
}

// ==================== 确认入库 ====================
async function openConfirmDialog(row) {
  confirmTarget.value = row
  confirmSubmitting.value = false
  try {
    const data = await getInboundDetail(row.id)
    // 为每行添加 _actualQty 字段，默认等于计划数
    confirmDetails.value = (data.details || []).map(d => ({
      ...d,
      _actualQty: d.planQty
    }))
  } catch {
    confirmDetails.value = []
  }
  confirmVisible.value = true
}

async function handleConfirmSubmit() {
  confirmSubmitting.value = true
  try {
    const details = confirmDetails.value.map(d => ({
      materialCode: d.materialCode,
      actualQty: d._actualQty
    }))
    await confirmInbound(confirmTarget.value.id, details)
    ElMessage.success('入库确认成功')
    confirmVisible.value = false
    loadOrders()
  } catch { /* */ } finally {
    confirmSubmitting.value = false
  }
}

// ==================== 修改入库单 ====================
async function searchEditMaterials(query, idx) {
  editMaterialLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    editMaterialOptions.value[idx] = data.records || []
  } catch {
    editMaterialOptions.value[idx] = []
  } finally {
    editMaterialLoading.value[idx] = false
  }
}

function addEditDetail() {
  editForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}
function removeEditDetail(idx) {
  if (editForm.details.length > 1) editForm.details.splice(idx, 1)
}

async function openEditDialog(row) {
  editTarget.value = row
  editSubmitting.value = false
  editMaterialOptions.value = {}
  try {
    const data = await getInboundDetail(row.id)
    editForm.supplierCode = data.supplierCode || ''
    editForm.details = (data.details || []).map(d => ({
      materialCode: d.materialCode || '',
      packCapacity: d.packCapacity || 20,
      planQty: d.planQty || 100
    }))
    if (editForm.details.length === 0) {
      editForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
    }
    editVisible.value = true
  } catch {
    ElMessage.error('加载入库单详情失败')
  }
}

async function handleEditSubmit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  const invalid = editForm.details.find(d =>
    !d.materialCode?.trim() || !d.packCapacity || !d.planQty
  )
  if (invalid) {
    ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  editSubmitting.value = true
  try {
    await updateInbound(editTarget.value.id, {
      supplierCode: editForm.supplierCode,
      details: editForm.details
    })
    ElMessage.success('入库单修改成功')
    editVisible.value = false
    loadOrders()
  } catch { /* */ } finally {
    editSubmitting.value = false
  }
}

// ==================== 入库单详情 ====================
async function openDetailDialog(row) {
  detailData.value = null
  detailVisible.value = true
  try {
    detailData.value = await getInboundDetail(row.id)
  } catch {
    detailVisible.value = false
  }
}

// ==================== 打印入库单 ====================
async function openPrintDialog(row) {
  printOrder.value = null
  printVisible.value = true
  try {
    printOrder.value = await getInboundDetail(row.id)
  } catch {
    ElMessage.error('加载入库单详情失败')
    printVisible.value = false
  }
}

function doPrint() {
  const printContent = document.getElementById('printInboundArea')
  if (!printContent) return
  const win = window.open('', '_blank', 'width=800,height=600')
  win.document.write(`
    <html><head><title>打印入库单</title>
    <style>
      body { font-family: 'Microsoft YaHei', sans-serif; padding: 20px; color: #333; }
      h2 { text-align: center; margin-bottom: 16px; font-size: 18px; }
      .print-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 6px;
        margin-bottom: 20px; padding: 10px; background: #f5f5f5; border-radius: 4px; }
      .print-meta span { font-size: 13px; }
      table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
      th, td { border: 1px solid #333; padding: 6px 10px; text-align: left; font-size: 14px; }
      th { background: #e0e0e0; }
      .print-footer { display: flex; justify-content: space-between;
        font-size: 12px; color: #666; margin-top: 20px; padding-top: 12px; border-top: 1px solid #ccc; }
      @media print { body { padding: 0; } }
    </style></head><body>
    ${printContent.innerHTML}
    </body></html>
  `)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 300)
}
</script>

<style scoped>
/* ==================== 布局 ==================== */
.inbound-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  margin-bottom: 14px;
  background: #f7f9fc;
  border: 1px solid var(--border-light);
  border-radius: 4px;
}
.summary-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.summary-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.summary-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: var(--text-regular);
  font-size: 12px;
}
.summary-stats span {
  padding: 4px 8px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 3px;
  white-space: nowrap;
}
.inbound-toolbar :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 新建入库单明细编辑器 ==================== */
.draft-alert {
  margin-bottom: 16px;
}
.detail-editor {
  width: 100%;
  min-width: 0;
}
.detail-head {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 128px 140px 74px;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.detail-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 128px 140px 74px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 10px;
}
.detail-row :deep(.el-input-number) {
  width: 100%;
}
.detail-row :deep(.el-input__wrapper) {
  min-width: 0;
}
.detail-row :deep(.el-button) {
  display: inline-flex;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding-left: 2px;
  padding-right: 2px;
}
.detail-actions {
  padding-top: 2px;
}
.detail-actions :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 对话框底部 ==================== */
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.footer-tip,
.muted-text {
  font-size: 12px;
  color: var(--text-secondary);
}

/* ==================== 确认入库 ==================== */
.confirm-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #f7f9fc;
  border-radius: 4px;
  font-size: 14px;
  color: var(--text-regular);
}
.confirm-label {
  color: var(--text-secondary);
}
.confirm-divider {
  color: var(--border-base);
  margin: 0 8px;
}

/* ==================== 详情 ==================== */
.detail-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.detail-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
}
.info-label {
  font-size: 12px;
  color: var(--text-secondary);
}
.info-value {
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 500;
}

/* ==================== 标签 ==================== */
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  white-space: nowrap;
}
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-default { background: #f4f4f5; color: #909399; }

/* ==================== 打印预览 ==================== */
.print-area {
  min-height: 200px;
}
.print-content h2 {
  text-align: center;
  margin-bottom: 12px;
  font-size: 18px;
}
.print-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 10px;
  background: #f7f9fc;
  border-radius: 4px;
  margin-bottom: 16px;
}
.print-meta span {
  font-size: 13px;
}
.print-table {
  width: 100%;
  border-collapse: collapse;
}
.print-table th, .print-table td {
  border: 1px solid var(--border-base);
  padding: 6px 10px;
  text-align: left;
  font-size: 13px;
}
.print-table th {
  background: #f0f2f5;
  font-weight: 600;
}
.print-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

/* ==================== 响应式 ==================== */
@media (max-width: 760px) {
  .inbound-summary,
  .dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-stats {
    flex-wrap: wrap;
  }
  .detail-head {
    display: none;
  }
  .detail-row {
    grid-template-columns: 1fr;
    padding: 10px;
    border: 1px solid var(--border-light);
    border-radius: 4px;
  }
  .detail-row :deep(.el-button) {
    justify-content: flex-start;
  }
  .detail-info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
