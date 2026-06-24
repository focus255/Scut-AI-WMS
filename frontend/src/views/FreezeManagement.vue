<!--
  库存封存解封管理页。
  @author Focus
  @date 2026-06-23
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <div class="block-header">
        <span class="block-title">封存管理</span>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button type="primary" size="small" @click="openSealDialog">
          <el-icon :size="14"><Lock /></el-icon> 封存二维码
        </el-button>
        <el-input v-model="searchMaterial" placeholder="物料号" size="small" clearable
          style="width: 160px" @keyup.enter="loadList" />
        <el-select v-model="searchStatus" placeholder="状态" size="small" clearable style="width: 120px"
          @change="loadList">
          <el-option label="封存中" value="FROZEN" />
          <el-option label="已解封" value="UNFROZEN" />
        </el-select>
        <el-button size="small" @click="loadList">查询</el-button>
      </div>

      <!-- 封存记录表 -->
      <el-table :data="freezeList" stripe size="small" v-loading="loading"
        empty-text="暂无封存记录" @row-click="openEditDialog" style="cursor: pointer">
        <el-table-column prop="barcode" label="看板号" min-width="240" show-overflow-tooltip />
        <el-table-column prop="materialCode" label="物料号" width="140" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="badge" :class="row.status === 'FROZEN' ? 'badge-warn' : 'badge-success'">
              {{ row.status === 'FROZEN' ? '封存中' : '已解封' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="freezeType" label="封存类型" width="100" align="center" />
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="freezeTime" label="封存时间" width="170" />
        <el-table-column prop="unfreezeTime" label="解封时间" width="170">
          <template #default="{ row }">{{ row.unfreezeTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'FROZEN'" type="success" link size="small"
              @click="handleUnseal(row)">
              解封
            </el-button>
            <span v-else class="muted-text">—</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top: 12px; display: flex; justify-content: flex-end">
        <el-pagination
          v-if="total > size"
          :current-page="page" :page-size="size" :total="total"
          layout="total, prev, pager, next" size="small"
          @current-change="loadList" />
      </div>
    </div>

    <!-- 封存对话框 -->
    <Teleport to="body">
      <el-dialog v-model="sealVisible" title="封存二维码" width="500px" destroy-on-close>
        <el-form :model="sealForm" label-width="80px">
          <el-form-item label="二维码列表" required>
            <el-input v-model="sealForm.barcodeInput" type="textarea" :rows="4"
              placeholder="输入看板号，每行一个或用逗号/空格分隔" />
          </el-form-item>
          <el-form-item label="封存类型" required>
            <el-select v-model="sealForm.freezeType" placeholder="选择封存类型" style="width: 100%">
              <el-option label="扫码" value="扫码" />
              <el-option label="质量问题" value="QUALITY" />
              <el-option label="管理封存" value="ADMIN" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="封存原因" required>
            <el-input v-model="sealForm.reason" placeholder="请输入封存原因" maxlength="200" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="sealVisible = false">取消</el-button>
          <el-button type="primary" :loading="sealSubmitting" @click="handleSeal">确认封存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 编辑封存详情 -->
    <Teleport to="body">
      <el-dialog v-model="editVisible" title="封存详情" width="500px" destroy-on-close>
        <el-form :model="editForm" label-width="80px" v-if="editForm.id">
          <el-form-item label="看板号">
            <span class="form-text">{{ editForm.barcode }}</span>
          </el-form-item>
          <el-form-item label="物料号">
            <span class="form-text">{{ editForm.materialCode }}</span>
          </el-form-item>
          <el-form-item label="封存类型">
            <el-select v-model="editForm.freezeType" style="width: 100%">
              <el-option label="扫码" value="扫码" />
              <el-option label="质量问题" value="QUALITY" />
              <el-option label="管理封存" value="ADMIN" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="封存原因">
            <el-input v-model="editForm.reason" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="操作人">
            <span class="form-text">{{ editForm.operator }}</span>
          </el-form-item>
          <el-form-item label="封存时间">
            <span class="form-text">{{ editForm.freezeTime }}</span>
          </el-form-item>
          <el-form-item label="状态">
            <span class="badge" :class="editForm.status === 'FROZEN' ? 'badge-warn' : 'badge-success'">
              {{ editForm.status === 'FROZEN' ? '封存中' : '已解封' }}
            </span>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editVisible = false">关闭</el-button>
          <el-button v-if="editForm.status === 'FROZEN'" type="primary" @click="handleEditSave">保存</el-button>
          <el-button v-if="editForm.status === 'FROZEN'" type="success" @click="handleEditUnseal">解封</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import request from '@/api/request'

const freezeList = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const searchMaterial = ref('')
const searchStatus = ref('')

const sealVisible = ref(false)
const sealSubmitting = ref(false)
const sealForm = reactive({ barcodeInput: '', freezeType: '扫码', reason: '' })

const editVisible = ref(false)
const editForm = reactive({ id: null, barcode: '', materialCode: '', freezeType: '', reason: '', operator: '', freezeTime: '', status: '' })

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (searchMaterial.value.trim()) params.materialCode = searchMaterial.value.trim()
    if (searchStatus.value) params.status = searchStatus.value
    const data = await request.get('/freeze/list', { params })
    freezeList.value = data.records || []
    total.value = data.total || 0
  } catch { freezeList.value = [] }
  finally { loading.value = false }
}

function openSealDialog() {
  sealForm.barcodeInput = ''
  sealForm.freezeType = 'QUALITY'
  sealForm.reason = ''
  sealVisible.value = true
}

/** 解析用户输入的二维码（逗号/空格/换行分隔） */
function parseBarcodeInput(input) {
  return input.split(/[,，\s\n\r]+/).map(s => s.trim()).filter(Boolean)
}

async function handleSeal() {
  const barcodes = parseBarcodeInput(sealForm.barcodeInput)
  if (barcodes.length === 0) { ElMessage.warning('请输入至少一个看板号'); return }
  if (!sealForm.reason.trim()) { ElMessage.warning('请填写封存原因'); return }
  sealSubmitting.value = true
  try {
    await request.post('/freeze/seal', {
      barcodes,
      freezeType: sealForm.freezeType,
      reason: sealForm.reason.trim()
    })
    ElMessage.success(`成功封存 ${barcodes.length} 个二维码`)
    sealVisible.value = false
    loadList()
  } catch (err) {
    ElMessage.error(err.message || '封存失败')
  } finally { sealSubmitting.value = false }
}

function openEditDialog(row) {
  editForm.id = row.id
  editForm.barcode = row.barcode
  editForm.materialCode = row.materialCode
  editForm.freezeType = row.freezeType
  editForm.reason = row.reason
  editForm.operator = row.operator
  editForm.freezeTime = row.freezeTime
  editForm.status = row.status
  editVisible.value = true
}

async function handleEditSave() {
  if (!editForm.reason.trim()) { ElMessage.warning('请填写封存原因'); return }
  try {
    await request.put(`/freeze/${editForm.id}`, null, {
      params: { freezeType: editForm.freezeType, reason: editForm.reason.trim() }
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    loadList()
  } catch (err) { ElMessage.error(err.message || '保存失败') }
}

async function handleEditUnseal() {
  try {
    await ElMessageBox.confirm(`确定解封二维码 ${editForm.barcode}？`, '确认解封', { type: 'warning' })
    await request.post('/freeze/unseal', null, { params: { barcode: editForm.barcode } })
    ElMessage.success('解封成功')
    editVisible.value = false
    loadList()
  } catch { /* */ }
}

async function handleUnseal(row) {
  try {
    await ElMessageBox.confirm(`确定解封二维码 ${row.barcode}？`, '确认解封', { type: 'warning' })
    await request.post('/freeze/unseal', null, { params: { barcode: row.barcode } })
    ElMessage.success('解封成功')
    loadList()
  } catch { /* 取消或失败 */ }
}
</script>

<style scoped>
.badge { display: inline-block; padding: 2px 8px; border-radius: 3px; font-size: 12px; }
.badge-warn { background: #fdf6ec; color: #e6a23c; }
.badge-success { background: #f0f9eb; color: #67c23a; }
.muted-text { font-size: 12px; color: var(--text-secondary); }
.form-text { font-size: 14px; color: var(--text-primary); }
</style>
