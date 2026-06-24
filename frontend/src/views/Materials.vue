<!--
  物料与基础数据管理页 — 三个子 Tab。
  @author Focus
  @date 2026-06-03
-->
<template>
  <div class="page-container">
    <div class="content-block">
      <el-tabs v-model="activeTab">
        <!-- ==================== 物料档案 ==================== -->
        <el-tab-pane label="物料档案" name="materials">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openMaterialDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增物料
            </el-button>
            <el-input v-model="materialKeyword" placeholder="搜索物料号或名称"
              clearable size="small" style="width: 240px" @input="loadMaterials" />
            <el-button size="small" @click="exportMaterials">导出 CSV</el-button>
          </div>
          <el-table :data="materialList" stripe size="small" v-loading="materialLoading" empty-text="暂无物料数据">
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="supplierCode" label="默认供应商" width="160" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openMaterialDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleMaterialDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="materialTotal > 10"
              :current-page="materialPage" :page-size="10" :total="materialTotal"
              layout="total, prev, pager, next" size="small"
              @current-change="loadMaterials" />
          </div>
        </el-tab-pane>

        <!-- ==================== 器具配置 ==================== -->
        <el-tab-pane label="器具配置" name="appliances">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openApplianceDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增器具
            </el-button>
            <el-input v-model="applianceKeyword" placeholder="搜索物料号/供应商/型号"
              clearable size="small" style="width: 240px" @input="loadAppliances" />
            <el-button size="small" @click="exportAppliances">导出 CSV</el-button>
          </div>
          <el-table :data="applianceList" stripe size="small" v-loading="applianceLoading" empty-text="暂无器具配置">
            <el-table-column prop="materialCode" label="物料号" width="140" />
            <el-table-column prop="supplierCode" label="供应商编码" width="160" />
            <el-table-column prop="packType" label="包装器具型号" min-width="150" />
            <el-table-column prop="packCapacity" label="单箱容量" width="90" align="right" />
            <el-table-column prop="createdAt" label="创建时间" width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openApplianceDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleApplianceDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="applianceTotal > 10"
              :current-page="appliancePage" :page-size="10" :total="applianceTotal"
              layout="total, prev, pager, next" size="small"
              @current-change="loadAppliances" />
          </div>
        </el-tab-pane>

        <!-- ==================== 供应商库 ==================== -->
        <el-tab-pane label="供应商库" name="suppliers">
          <div class="toolbar">
            <el-button type="primary" size="small" @click="openSupplierDialog(null)">
              <el-icon :size="14" style="margin-right: 4px"><Plus /></el-icon>新增供应商
            </el-button>
            <el-input v-model="supplierKeyword" placeholder="搜索供应商编码或名称"
              clearable size="small" style="width: 240px" @input="loadSuppliers" />
            <el-button size="small" @click="exportSuppliers">导出 CSV</el-button>
          </div>
          <el-table :data="supplierList" stripe size="small" v-loading="supplierLoading" empty-text="暂无供应商数据">
            <el-table-column prop="supplierCode" label="供应商编码" width="160" />
            <el-table-column prop="supplierName" label="供应商名称" min-width="180" />
            <el-table-column prop="contactName" label="联系人" width="100" />
            <el-table-column prop="contactPhone" label="联系电话" width="140" />
            <el-table-column prop="createdAt" label="创建时间" width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openSupplierDialog(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleSupplierDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="margin-top: 12px; display: flex; justify-content: flex-end">
            <el-pagination
              v-if="supplierTotal > 10"
              :current-page="supplierPage" :page-size="10" :total="supplierTotal"
              layout="total, prev, pager, next" size="small"
              @current-change="loadSuppliers" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ========== 物料新增 / 编辑对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="materialDialogVisible" :title="materialDialogTitle" width="480px" destroy-on-close>
        <el-form ref="materialFormRef" :model="materialForm" :rules="materialRules" label-width="100px">
          <el-form-item label="物料号" prop="materialCode">
            <el-input v-model="materialForm.materialCode" :disabled="!!materialEditingRow" placeholder="如 M_PART_001" />
          </el-form-item>
          <el-form-item label="物料名称" prop="materialName">
            <el-input v-model="materialForm.materialName" placeholder="如 左前大灯总成" />
          </el-form-item>
          <el-form-item label="默认供应商" prop="supplierCode">
            <el-input v-model="materialForm.supplierCode" placeholder="如 SUP_VWG_09" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="materialDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleMaterialSave">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- ========== 物料删除确认对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="materialDeleteVisible" title="删除确认" width="400px"
        :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#f56c6c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          确定删除物料 {{ materialDeleteTarget?.materialCode }}？
        </p>
        <template #footer>
          <el-button @click="materialDeleteVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmMaterialDelete">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- ========== 供应商新增 / 编辑对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="supplierDialogVisible" :title="supplierDialogTitle" width="480px" destroy-on-close>
        <el-form ref="supplierFormRef" :model="supplierForm" :rules="supplierRules" label-width="100px">
          <el-form-item label="供应商编码" prop="supplierCode">
            <el-input v-model="supplierForm.supplierCode" :disabled="!!supplierEditingRow" placeholder="如 SUP_VWG_09" />
          </el-form-item>
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input v-model="supplierForm.supplierName" placeholder="如一汽大众佛山配件厂" />
          </el-form-item>
          <el-form-item label="联系人">
            <el-input v-model="supplierForm.contactName" placeholder="如 李经理" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="supplierForm.contactPhone" placeholder="如 13800000001" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="supplierDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSupplierSave">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- ========== 供应商删除确认对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="supplierDeleteVisible" title="删除确认" width="400px"
        :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#f56c6c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          确定删除供应商 {{ supplierDeleteTarget?.supplierName }}（{{ supplierDeleteTarget?.supplierCode }}）？
        </p>
        <template #footer>
          <el-button @click="supplierDeleteVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmSupplierDelete">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- ========== 器具新增 / 编辑对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="applianceDialogVisible" :title="applianceDialogTitle" width="480px" destroy-on-close>
        <el-form ref="applianceFormRef" :model="applianceForm" :rules="applianceRules" label-width="110px">
          <el-form-item label="物料号" prop="materialCode">
            <el-input v-model="applianceForm.materialCode" :disabled="!!applianceEditingRow" placeholder="如 M_PART_001" />
          </el-form-item>
          <el-form-item label="供应商编码" prop="supplierCode">
            <el-input v-model="applianceForm.supplierCode" placeholder="如 SUP_VWG_09" />
          </el-form-item>
          <el-form-item label="包装器具型号" prop="packType">
            <el-input v-model="applianceForm.packType" placeholder="如 标准铁箱" />
          </el-form-item>
          <el-form-item label="单箱容量" prop="packCapacity">
            <el-input-number v-model="applianceForm.packCapacity" :min="1" :max="999999"
              controls-position="right" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="applianceDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleApplianceSave">保存</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- ========== 器具删除确认对话框 ========== -->
    <Teleport to="body">
      <el-dialog v-model="applianceDeleteVisible" title="删除确认" width="400px"
        :close-on-click-modal="false" destroy-on-close>
        <p style="font-size: 15px; text-align: center; padding: 10px 0;">
          <el-icon :size="22" color="#f56c6c" style="vertical-align: middle; margin-right: 6px;">
            <WarningFilled />
          </el-icon>
          确定删除物料 {{ applianceDeleteTarget?.materialCode }} 的器具配置？
        </p>
        <template #footer>
          <el-button @click="applianceDeleteVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmApplianceDelete">确定</el-button>
        </template>
      </el-dialog>
    </Teleport>
  </div>
</template>

<script setup>
/**
 * 物料与基础数据管理 — 物料档案 + 器具配置 + 供应商库。
 */
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, WarningFilled } from '@element-plus/icons-vue'
import { getMaterials, createMaterial, updateMaterial, deleteMaterial } from '@/api/materials'
import { getSuppliers, createSupplier, updateSupplier, deleteSupplier } from '@/api/suppliers'
import { getAppliances, createAppliance, updateAppliance, deleteAppliance } from '@/api/appliances'
import { exportCSV } from '@/utils/export'

const activeTab = ref('materials')

// ==================== 物料管理 ====================
const materialList = ref([])
const materialLoading = ref(false)
const materialPage = ref(1)
const materialTotal = ref(0)
const materialKeyword = ref('')

const materialDialogVisible = ref(false)
const materialEditingRow = ref(null)
const materialFormRef = ref(null)

const materialDeleteVisible = ref(false)
const materialDeleteTarget = ref(null)

const materialForm = reactive({ materialCode: '', materialName: '', supplierCode: '' })
const materialRules = {
  materialCode: [{ required: true, message: '请输入物料号', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  supplierCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }]
}
const materialDialogTitle = computed(() => materialEditingRow.value ? '编辑物料' : '新增物料')

// ==================== 供应商管理 ====================
const supplierList = ref([])
const supplierLoading = ref(false)
const supplierPage = ref(1)
const supplierTotal = ref(0)
const supplierKeyword = ref('')

const supplierDialogVisible = ref(false)
const supplierEditingRow = ref(null)
const supplierFormRef = ref(null)

const supplierDeleteVisible = ref(false)
const supplierDeleteTarget = ref(null)

const supplierForm = reactive({ supplierCode: '', supplierName: '', contactName: '', contactPhone: '' })
const supplierRules = {
  supplierCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}
const supplierDialogTitle = computed(() => supplierEditingRow.value ? '编辑供应商' : '新增供应商')

// ==================== 器具管理 ====================
const applianceList = ref([])
const applianceLoading = ref(false)
const appliancePage = ref(1)
const applianceTotal = ref(0)
const applianceKeyword = ref('')

const applianceDialogVisible = ref(false)
const applianceEditingRow = ref(null)
const applianceFormRef = ref(null)

const applianceDeleteVisible = ref(false)
const applianceDeleteTarget = ref(null)

const applianceForm = reactive({ materialCode: '', supplierCode: '', packType: '', packCapacity: 20 })
const applianceRules = {
  materialCode: [{ required: true, message: '请输入物料号', trigger: 'blur' }],
  supplierCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  packType: [{ required: true, message: '请输入包装器具型号', trigger: 'blur' }],
  packCapacity: [{ required: true, message: '请输入单箱容量', trigger: 'blur' }]
}
const applianceDialogTitle = computed(() => applianceEditingRow.value ? '编辑器具' : '新增器具')

// ==================== 初始化 ====================
onMounted(() => loadMaterials())

/**
 * 切换 Tab 时自动加载对应数据（防止首次点击 tab 时表格为空）。
 */
watch(activeTab, (tab) => {
  if (tab === 'suppliers' && supplierList.value.length === 0) loadSuppliers()
  if (tab === 'appliances' && applianceList.value.length === 0) loadAppliances()
})

// ==================== 物料 CRUD ====================
async function loadMaterials(page = 1) {
  materialPage.value = page
  materialLoading.value = true
  try {
    const data = await getMaterials({ page, size: 10, keyword: materialKeyword.value })
    materialList.value = data.records || []
    materialTotal.value = data.total || 0
  } catch {
    ElMessage.error('加载物料失败')
  } finally {
    materialLoading.value = false
  }
}

function openMaterialDialog(row) {
  materialEditingRow.value = row
  if (row) {
    Object.assign(materialForm, { materialCode: row.materialCode, materialName: row.materialName, supplierCode: row.supplierCode })
  } else {
    materialFormRef.value?.resetFields()
  }
  materialDialogVisible.value = true
}

async function handleMaterialSave() {
  const valid = await materialFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (materialEditingRow.value) {
      await updateMaterial(materialEditingRow.value.id, { ...materialForm })
      ElMessage.success('物料更新成功')
    } else {
      await createMaterial({ ...materialForm })
      ElMessage.success('物料创建成功')
    }
    materialDialogVisible.value = false
    loadMaterials(materialPage.value)
  } catch (err) {
    ElMessage.error(err?.message || '保存物料失败')
  }
}

function handleMaterialDelete(row) {
  materialDeleteTarget.value = row
  materialDeleteVisible.value = true
}

async function confirmMaterialDelete() {
  if (!materialDeleteTarget.value) return
  try {
    await deleteMaterial(materialDeleteTarget.value.id)
    ElMessage.success('已删除')
    materialDeleteVisible.value = false
    materialDeleteTarget.value = null
    loadMaterials(materialPage.value)
  } catch (err) {
    ElMessage.error(err?.message || '删除物料失败')
  }
}

// ==================== 供应商 CRUD ====================
async function loadSuppliers(page = 1) {
  supplierPage.value = page
  supplierLoading.value = true
  try {
    const data = await getSuppliers({ page, size: 10, keyword: supplierKeyword.value })
    supplierList.value = data.records || []
    supplierTotal.value = data.total || 0
  } catch {
    ElMessage.error('加载供应商失败')
  } finally {
    supplierLoading.value = false
  }
}

function openSupplierDialog(row) {
  supplierEditingRow.value = row
  if (row) {
    Object.assign(supplierForm, {
      supplierCode: row.supplierCode,
      supplierName: row.supplierName,
      contactName: row.contactName || '',
      contactPhone: row.contactPhone || ''
    })
  } else {
    supplierFormRef.value?.resetFields()
  }
  supplierDialogVisible.value = true
}

async function handleSupplierSave() {
  const valid = await supplierFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (supplierEditingRow.value) {
      await updateSupplier(supplierEditingRow.value.id, { ...supplierForm })
      ElMessage.success('供应商更新成功')
    } else {
      await createSupplier({ ...supplierForm })
      ElMessage.success('供应商创建成功')
    }
    supplierDialogVisible.value = false
    loadSuppliers(supplierPage.value)
  } catch (err) {
    ElMessage.error(err?.message || '保存供应商失败')
  }
}

function handleSupplierDelete(row) {
  supplierDeleteTarget.value = row
  supplierDeleteVisible.value = true
}

async function confirmSupplierDelete() {
  if (!supplierDeleteTarget.value) return
  try {
    await deleteSupplier(supplierDeleteTarget.value.id)
    ElMessage.success('已删除')
    supplierDeleteVisible.value = false
    supplierDeleteTarget.value = null
    loadSuppliers(supplierPage.value)
  } catch (err) {
    ElMessage.error(err?.message || '删除供应商失败')
  }
}

// ==================== 器具 CRUD ====================
async function loadAppliances(page = 1) {
  appliancePage.value = page
  applianceLoading.value = true
  try {
    const data = await getAppliances({ page, size: 10, keyword: applianceKeyword.value })
    applianceList.value = data.records || []
    applianceTotal.value = data.total || 0
  } catch {
    ElMessage.error('加载器具数据失败')
  } finally {
    applianceLoading.value = false
  }
}

function openApplianceDialog(row) {
  applianceEditingRow.value = row
  if (row) {
    Object.assign(applianceForm, {
      materialCode: row.materialCode,
      supplierCode: row.supplierCode,
      packType: row.packType,
      packCapacity: row.packCapacity
    })
  } else {
    applianceFormRef.value?.resetFields()
  }
  applianceDialogVisible.value = true
}

async function handleApplianceSave() {
  const valid = await applianceFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    if (applianceEditingRow.value) {
      await updateAppliance(applianceEditingRow.value.id, { ...applianceForm })
      ElMessage.success('器具更新成功')
    } else {
      await createAppliance({ ...applianceForm })
      ElMessage.success('器具创建成功')
    }
    applianceDialogVisible.value = false
    loadAppliances(appliancePage.value)
  } catch (err) {
    ElMessage.error(err?.message || '保存器具失败')
  }
}

function handleApplianceDelete(row) {
  applianceDeleteTarget.value = row
  applianceDeleteVisible.value = true
}

async function confirmApplianceDelete() {
  if (!applianceDeleteTarget.value) return
  try {
    await deleteAppliance(applianceDeleteTarget.value.id)
    ElMessage.success('已删除')
    applianceDeleteVisible.value = false
    applianceDeleteTarget.value = null
    loadAppliances(appliancePage.value)
  } catch (err) {
    ElMessage.error(err?.message || '删除器具失败')
  }
}

// ==================== 导出 ====================
function exportMaterials() {
  if (!materialList.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'materialCode', label: '物料号' },
    { key: 'materialName', label: '物料名称' },
    { key: 'supplierCode', label: '默认供应商' }
  ], materialList.value, `物料档案_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
function exportSuppliers() {
  if (!supplierList.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'supplierCode', label: '供应商编码' },
    { key: 'supplierName', label: '供应商名称' },
    { key: 'contactName', label: '联系人' },
    { key: 'contactPhone', label: '联系电话' },
    { key: 'createdAt', label: '创建时间' }
  ], supplierList.value, `供应商库_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
function exportAppliances() {
  if (!applianceList.value.length) { ElMessage.warning('没有数据可导出'); return }
  exportCSV([
    { key: 'materialCode', label: '物料号' },
    { key: 'supplierCode', label: '供应商编码' },
    { key: 'packType', label: '包装器具型号' },
    { key: 'packCapacity', label: '单箱容量' },
    { key: 'createdAt', label: '创建时间' }
  ], applianceList.value, `器具配置_${new Date().toISOString().substring(0, 10)}`)
  ElMessage.success('导出成功')
}
</script>
