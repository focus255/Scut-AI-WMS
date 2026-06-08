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
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openInboundDialog">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新建入库单
            </el-button>
          </div>
          <el-table :data="inboundList" stripe size="small" v-loading="inboundLoading">
            <el-table-column prop="orderNo" label="入库单号" width="180" />
            <el-table-column prop="supplierCode" label="供应商" width="160" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '已完成' ? 'badge-success' : 'badge-default'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="140" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click="handleConfirm(row)">
                  确认入库
                </el-button>
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
      <el-dialog v-model="dialogVisible" title="新建入库单" width="560px" destroy-on-close>
        <el-form ref="formRef" :model="inboundForm" :rules="inboundRules" label-width="100px">
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="inboundForm.supplierCode" placeholder="请选择供应商" style="width: 100%">
              <el-option label="一汽大众佛山配件厂 (SUP_VWG_09)" value="SUP_VWG_09" />
              <el-option label="博世汽车部件苏州 (SUP_BOSCH_01)" value="SUP_BOSCH_01" />
              <el-option label="大陆汽车电子芜湖 (SUP_CONT_03)" value="SUP_CONT_03" />
            </el-select>
          </el-form-item>
          <el-form-item label="物料明细" prop="details">
            <div v-for="(item, idx) in inboundForm.details" :key="idx" class="detail-row">
              <el-input v-model="item.materialCode" placeholder="物料号" size="small" style="width: 150px" />
              <span class="detail-sep">包装</span>
              <el-input-number v-model="item.packCapacity" :min="1" size="small" style="width: 100px" />
              <span class="detail-sep">计划数</span>
              <el-input-number v-model="item.planQty" :min="1" size="small" style="width: 110px" />
              <el-button type="danger" link size="small" @click="removeDetail(idx)"
                :disabled="inboundForm.details.length <= 1" style="margin-left: 4px">
                <el-icon :size="14"><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" link size="small" @click="addDetail" style="margin-top: 4px">
              + 添加物料行
            </el-button>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleCreate">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 入库与出库管理。
 */
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInboundOrders, createInbound, confirmInbound } from '@/api/inbound'

const route = useRoute()
const router = useRouter()

const activeTab = ref('inbound')

// 入库
const inboundList = ref([])
const inboundLoading = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const inboundForm = reactive({
  supplierCode: '',
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})
const inboundRules = {
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

onMounted(() => {
  loadOrders()
  applyAiInboundDraft()
})

watch(
  () => route.query,
  () => applyAiInboundDraft()
)

async function loadOrders() {
  inboundLoading.value = true
  try {
    const data = await getInboundOrders({ page: 1, size: 50 })
    inboundList.value = data.records || []
  } catch { /* */ } finally {
    inboundLoading.value = false
  }
}

function addDetail() {
  inboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}
function removeDetail(idx) {
  if (inboundForm.details.length > 1) inboundForm.details.splice(idx, 1)
}

function openInboundDialog() {
  inboundForm.supplierCode = ''
  inboundForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
  dialogVisible.value = true
}

function applyAiInboundDraft() {
  const materialCode = String(route.query.materialCode || '').trim()
  const suggestedQty = Number(route.query.suggestedQty || 0)
  if (!materialCode || suggestedQty <= 0) return

  activeTab.value = 'inbound'
  inboundForm.supplierCode = ''
  inboundForm.details = [{
    materialCode,
    packCapacity: 20,
    planQty: suggestedQty
  }]
  dialogVisible.value = true
  ElMessage.info('已根据 AI 建议预填入库明细，请选择供应商后保存')

  router.replace({ path: route.path, query: {} })
}

async function handleCreate() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
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

async function handleConfirm(row) {
  try {
    await confirmInbound(row.id)
    ElMessage.success('入库确认成功')
    loadOrders()
  } catch { /* */ }
}
</script>

<style scoped>
.detail-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}
.detail-sep {
  font-size: 12px;
  color: var(--text-secondary);
  width: 36px;
}
.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
}
.badge-success { background: #f0f9eb; color: #67c23a; }
.badge-default { background: #f4f4f5; color: #909399; }
</style>
