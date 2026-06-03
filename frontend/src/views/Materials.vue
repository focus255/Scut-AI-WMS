<!--
  物料与基础数据管理页 — 三个子 Tab。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab">
        <!-- === 物料档案 === -->
        <el-tab-pane label="物料档案" name="materials">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增物料
            </el-button>
            <el-input v-model="materialKeyword" placeholder="搜索物料编码或名称"
              clearable size="small" style="width: 240px" @input="loadMaterials" />
          </div>
          <el-table :data="materialList" stripe size="small" v-loading="materialLoading">
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="supplierCode" label="默认供应商" width="160" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="materialTotal > 10"
              :current-page="materialPage" :page-size="10" :total="materialTotal"
              layout="total, prev, pager, next" small
              @current-change="loadMaterials" />
          </div>
        </el-tab-pane>

        <!-- === 器具配置 === -->
        <el-tab-pane label="器具配置" name="appliances">
          <div class="empty-hint">器具配置功能开发中</div>
        </el-tab-pane>

        <!-- === 供应商库 === -->
        <el-tab-pane label="供应商库" name="suppliers">
          <div class="empty-hint">供应商管理功能开发中</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新增 / 编辑物料对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="物料号" prop="materialCode">
            <el-input v-model="form.materialCode" :disabled="!!editingRow" placeholder="如 M_PART_001" />
          </el-form-item>
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="form.materialName" placeholder="如 左前大灯总成" />
          </el-form-item>
          <el-form-item label="默认供应商" prop="supplierCode">
            <el-input v-model="form.supplierCode" placeholder="如 SUP_VWG_09" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 删除确认对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="deleteVisible" title="删除确认" width="400px"
        :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#f56c6c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          确定删除物料 {{ deleteTarget?.materialCode }}？
        </p>
        <template #footer>
          <el-button @click="deleteVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmDelete">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 物料与基础数据管理。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMaterials, createMaterial, updateMaterial, deleteMaterial } from '@/api/materials'

const activeTab = ref('materials')

// 物料管理
const materialList = ref([])
const materialLoading = ref(false)
const materialPage = ref(1)
const materialTotal = ref(0)
const materialKeyword = ref('')

const dialogVisible = ref(false)
const editingRow = ref(null)
const formRef = ref(null)

// 删除确认（模板式 Teleport 对话框，规避命令式 API 时序问题）
const deleteVisible = ref(false)
const deleteTarget = ref(null)

const form = reactive({ materialCode: '', materialName: '', supplierCode: '' })
const rules = {
  materialCode: [{ required: true, message: '请输入物料号', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  supplierCode: [{ required: true, message: '请输入供应商代码', trigger: 'blur' }]
}

const dialogTitle = computed(() => editingRow.value ? '编辑物料' : '新增物料')

onMounted(() => loadMaterials())

async function loadMaterials(page = 1) {
  materialPage.value = page
  materialLoading.value = true
  try {
    const data = await getMaterials({ page, size: 10, keyword: materialKeyword.value })
    materialList.value = data.records || []
    materialTotal.value = data.total || 0
  } catch { /* */ } finally {
    materialLoading.value = false
  }
}

function openDialog(row) {
  editingRow.value = row
  if (row) {
    Object.assign(form, { materialCode: row.materialCode, materialName: row.materialName, supplierCode: row.supplierCode })
  } else {
    formRef.value?.resetFields()
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingRow.value) {
      await updateMaterial(editingRow.value.id, { ...form })
      ElMessage.success('物料更新成功')
    } else {
      await createMaterial({ ...form })
      ElMessage.success('物料创建成功')
    }
    dialogVisible.value = false
    loadMaterials(materialPage.value)
  } catch { /* */ }
}

function handleDelete(row) {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await deleteMaterial(deleteTarget.value.id)
    ElMessage.success('已删除')
    deleteVisible.value = false
    deleteTarget.value = null
    loadMaterials(materialPage.value)
  } catch { /* */ }
}
</script>
