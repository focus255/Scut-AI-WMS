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
          <div class="history-stats">
            <span>待入库 <b>{{ pendingCount }}</b></span>
            <span>已完成 <b>{{ completedCount }}</b></span>
          </div>

          <div class="toolbar inbound-toolbar">
            <el-button type="primary" size="small" @click="openInboundDialog">
              <el-icon :size="14"><Plus /></el-icon>
              <span>新建入库单</span>
            </el-button>
            <el-button size="small" @click="openInboundFlowDialog">入库流水</el-button>
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
            <el-table-column label="操作" width="280" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="primary" link size="small"
                  @click.stop="openEditDialog(row)">编辑</el-button>
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click.stop="openConfirmDialog(row)">确认入库</el-button>
                <el-button type="danger" link size="small" @click.stop="handleInboundDelete(row)">
                  <el-icon :size="14"><Delete /></el-icon>删除
                </el-button>
                <el-button link size="small" @click.stop="printBarcodes(row)">
                  <el-icon :size="14"><Printer /></el-icon>打印看板
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- === 出库管理 === -->
        <el-tab-pane label="出库管理" name="outbound">
          <div class="history-stats">
            <span>待出库 <b>{{ outPendingCount }}</b></span>
            <span>已完成 <b>{{ outCompletedCount }}</b></span>
          </div>

          <div class="toolbar inbound-toolbar">
            <el-button type="primary" size="small" @click="openOutboundDialog">
              <el-icon :size="14"><Plus /></el-icon>
              <span>新建出库单</span>
            </el-button>
            <el-button size="small" @click="openHistoryDialog">出库流水</el-button>
            <el-button size="small" :loading="outboundLoading" @click="loadOutboundOrders">刷新</el-button>
          </div>
          <el-table :data="outboundList" stripe size="small" v-loading="outboundLoading"
            empty-text="暂无出库单数据" @row-click="openOutDetailDialog" style="cursor: pointer">
            <el-table-column prop="orderNo" label="出库单号" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <span class="badge" :class="outStatusClass(row.status)">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
            <el-table-column label="操作" width="300" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="primary" link size="small"
                  @click.stop="openOutEditDialog(row)">编辑</el-button>
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click.stop="openOutConfirmDialog(row)">确认出库</el-button>
                <el-button type="danger" link size="small" @click.stop="handleOutDelete(row)">
                  <el-icon :size="14"><Delete /></el-icon>删除
                </el-button>
                <el-button link size="small" @click.stop="printBarcodes(row)">
                  <el-icon :size="14"><Printer /></el-icon>打印看板
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 新建入库单右侧抽屉 -->
    <el-drawer v-model="dialogVisible" title="新建入库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
>
        <el-alert v-if="isAiDraft" title="已根据 AI 建议预填物料和计划数量，请选择供应商后保存。"
          type="info" show-icon :closable="false" class="draft-alert" />
        <el-form ref="formRef" :model="inboundForm" :rules="inboundRules" label-width="88px">
          <el-form-item label="供应商" prop="selectedSuppliers" :rules="[{ type: 'array', min: 1, message: '请至少添加一个供应商', trigger: 'change' }]">
            <div class="supplier-tags">
              <el-tag v-for="sc in inboundForm.selectedSuppliers" :key="sc"
                closable size="default" @close="removeSupplier(sc)"
                type="primary" effect="light">
                {{ getSupplierName(sc) }}
              </el-tag>
              <span class="supplier-pick-group">
                <el-select v-model="pendingSuppliers" placeholder="选择供应商"
                  multiple filterable size="small" style="width: 220px"
                  :disabled="availableSuppliers.length === 0"
                  popper-class="supplier-select-popper"
                  collapse-tags collapse-tags-tooltip :max-collapse-tags="1">
                  <el-option v-for="s in availableSuppliers" :key="s.supplierCode"
                    :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
                </el-select>
                <el-button size="small" type="primary" :disabled="pendingSuppliers.length === 0"
                  @click="confirmAddSuppliers" :icon="Plus" circle />
              </span>
            </div>
          </el-form-item>

          <!-- 物料管理：已选摘要 + 弹窗入口 -->
          <el-form-item v-if="inboundForm.selectedSuppliers.length > 0" label="物料管理">
            <div class="material-summary">
              <div v-if="materialCount(inboundForm.details) === 0" class="summary-empty">
                尚未选择物料，请点击下方按钮添加
              </div>
              <el-table v-else :data="inboundForm.details.filter(d => d.materialCode)" size="small" max-height="240">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="materialCode" label="物料号" width="140" />
                <el-table-column label="器具类型" width="100">
                  <template #default="{ row }">{{ row.packType || '—' }}</template>
                </el-table-column>
                <el-table-column label="单箱容量" width="80" align="right">
                  <template #default="{ row }">{{ row.packCapacity || '—' }}</template>
                </el-table-column>
                <el-table-column label="入库箱数" width="90" align="right">
                  <template #default="{ row }">{{ row.boxCount }}</template>
                </el-table-column>
                <el-table-column label="总件数" width="90" align="right">
                  <template #default="{ row }">{{ row.planQty || (row.boxCount * row.packCapacity) }}</template>
                </el-table-column>
              </el-table>
              <div class="summary-footer">
                <span>
                  共 <b>{{ materialCount(inboundForm.details) }}</b> 种物料 /
                  <b>{{ inboundTotalBoxes }}</b> 箱 /
                  <b>{{ inboundTotalQty }}</b> 件
                </span>
                <el-button type="primary" size="small" @click="openMaterialPicker('inbound')">
                  <el-icon :size="14"><Plus /></el-icon>
                  管理物料明细
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">
              合计 {{ inboundForm.details.filter(d => d.materialCode).length }} 种物料，
              {{ inboundTotalBoxes }} 箱，{{ inboundTotalQty }} 件
            </span>
            <div>
              <el-button @click="dialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleCreate">保存入库单</el-button>
            </div>
          </div>
        </template>
    </el-drawer>

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
          <el-table-column prop="materialCode" label="物料号" min-width="150" />
          <el-table-column prop="planQty" label="计划入库数" width="120" align="center" />
          <el-table-column label="实际到货数" width="160" align="center">
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
        width="min(680px, calc(100vw - 32px))" destroy-on-close>
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
          <el-table-column prop="materialCode" label="物料号" min-width="140" />
          <el-table-column prop="packCapacity" label="单箱容量" width="100" align="center" />
          <el-table-column prop="planQty" label="计划数" width="100" align="center" />
          <el-table-column prop="actualQty" label="实收数" width="100" align="center" />
        </el-table>
        <!-- 入库看板（点击可下载完整标签 PNG） -->
        <div v-if="detailData && detailData.barcodes && detailData.barcodes.length > 0"
          class="barcode-gallery">
          <div class="barcode-gallery-title">入库看板（共 {{ detailData.barcodes.length }} 个）</div>
          <div class="label-grid">
            <div v-for="bc in detailData.barcodes" :key="bc.barcode" class="label-card"
              @click="downloadLabel(bc, $event)">
              <div class="label-card-header">
                <el-icon :size="14" class="download-icon"><Download /></el-icon>
              </div>
              <BoxLabel :ref="el => setLabelRef(bc.barcode, el)"
                :barcode="bc.barcode"
                :created-at="bc.createdAt" />
              <div class="label-stamp" :class="'stamp-' + stampClass(bc.status)">{{ bc.status }}</div>
            </div>
          </div>
        </div>
        <!-- 入库流水 -->
        <div v-if="detailData && detailData.barcodes && detailData.barcodes.length > 0"
          style="margin-top: 16px">
          <div class="barcode-gallery-title">入库流水（共 {{ detailData.barcodes.length }} 条）</div>
          <el-table :data="detailData.barcodes" stripe size="small">
            <el-table-column prop="barcode" label="看板号" min-width="260" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <span class="badge" :class="row.status === '在库' ? 'badge-success' : row.status === '已出库' ? 'badge-default' : 'badge-warn'">
                  {{ row.status }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="remainingQty" label="剩余数量" width="90" align="right" />
            <el-table-column prop="createdAt" label="生成时间" width="170" show-overflow-tooltip />
          </el-table>
        </div>
        <div v-else class="empty-hint" style="padding: 30px 0">加载中...</div>
        <template #footer>
          <el-button @click="detailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 修改入库单右侧抽屉 -->
    <el-drawer v-model="editVisible" title="修改入库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
>
        <el-alert title="修改后需重新确认入库，原确认信息将被清除。"
          type="warning" show-icon :closable="false" class="draft-alert" />
        <el-form ref="editFormRef" :model="editForm" :rules="inboundRules" label-width="88px">
          <el-form-item label="供应商" prop="supplierCode">
            <el-select v-model="editForm.supplierCode" placeholder="请选择供应商" style="width: 100%"
              filterable @change="onEditSupplierChange">
              <el-option v-for="s in supplierOptions" :key="s.supplierCode"
                :label="`${s.supplierName} (${s.supplierCode})`" :value="s.supplierCode" />
            </el-select>
          </el-form-item>

          <!-- 物料管理：已选摘要 + 弹窗入口 -->
          <el-form-item v-if="editForm.supplierCode" label="物料管理">
            <div class="material-summary">
              <div v-if="materialCount(editForm.details) === 0" class="summary-empty">
                尚未选择物料，请点击下方按钮添加
              </div>
              <el-table v-else :data="editForm.details.filter(d => d.materialCode)" size="small" max-height="240">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="materialCode" label="物料号" width="140" />
                <el-table-column label="器具类型" width="100">
                  <template #default="{ row }">{{ row.packType || '—' }}</template>
                </el-table-column>
                <el-table-column label="单箱容量" width="80" align="right">
                  <template #default="{ row }">{{ row.packCapacity || '—' }}</template>
                </el-table-column>
                <el-table-column label="入库箱数" width="90" align="right">
                  <template #default="{ row }">{{ row.boxCount }}</template>
                </el-table-column>
                <el-table-column label="总件数" width="90" align="right">
                  <template #default="{ row }">{{ row.planQty || (row.boxCount * row.packCapacity) }}</template>
                </el-table-column>
              </el-table>
              <div class="summary-footer">
                <span>
                  共 <b>{{ materialCount(editForm.details) }}</b> 种物料 /
                  <b>{{ editInboundTotalBoxes }}</b> 箱 /
                  <b>{{ editInboundTotalQty }}</b> 件
                </span>
                <el-button type="primary" size="small" @click="openMaterialPicker('editInbound')">
                  <el-icon :size="14"><Plus /></el-icon>
                  管理物料明细
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">修改后需重新确认入库。合计 {{ editInboundTotalBoxes }} 箱，{{ editInboundTotalQty }} 件</span>
            <div>
              <el-button @click="editVisible = false">取消</el-button>
              <el-button type="primary" :loading="editSubmitting" @click="handleEditSubmit">保存修改</el-button>
            </div>
          </div>
        </template>
    </el-drawer>

    <!-- 新建出库单右侧抽屉 -->
    <el-drawer v-model="outDialogVisible" title="新建出库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
>
        <el-form ref="outFormRef" :model="outboundForm" label-width="88px">

          <!-- 物料管理：已选摘要 + 弹窗入口（出库无供应商限制） -->
          <el-form-item label="物料管理">
            <div class="material-summary">
              <div v-if="materialCount(outboundForm.details) === 0" class="summary-empty">
                尚未选择物料，请点击下方按钮添加
              </div>
              <el-table v-else :data="outboundForm.details.filter(d => d.materialCode)" size="small" max-height="240">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="materialCode" label="物料号" width="140" />
                <el-table-column label="器具类型" width="100">
                  <template #default="{ row }">{{ row.packType || '—' }}</template>
                </el-table-column>
                <el-table-column label="单箱容量" width="80" align="right">
                  <template #default="{ row }">{{ row.packCapacity || '—' }}</template>
                </el-table-column>
                <el-table-column label="出库箱数" width="90" align="right">
                  <template #default="{ row }">{{ row.boxCount }}</template>
                </el-table-column>
                <el-table-column label="总件数" width="90" align="right">
                  <template #default="{ row }">{{ row.planQty || (row.boxCount * row.packCapacity) }}</template>
                </el-table-column>
              </el-table>
              <div class="summary-footer">
                <span>
                  共 <b>{{ materialCount(outboundForm.details) }}</b> 种物料 /
                  <b>{{ outTotalBoxes }}</b> 箱 /
                  <b>{{ outTotalQty }}</b> 件
                </span>
                <el-button type="primary" size="small" @click="openMaterialPicker('outbound')">
                  <el-icon :size="14"><Plus /></el-icon>
                  管理物料明细
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">合计 {{ outTotalBoxes }} 箱，{{ outTotalQty }} 件</span>
            <div>
              <el-button @click="outDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleOutCreate">保存出库单</el-button>
            </div>
          </div>
        </template>
    </el-drawer>

    <!-- 确认出库对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="outConfirmVisible" title="手工确认出库"
        width="min(680px, calc(100vw - 32px))" destroy-on-close>
        <div class="confirm-info">
          <span class="confirm-label">出库单号：</span>
          <strong>{{ outConfirmTarget?.orderNo }}</strong>
        </div>
        <el-table :data="outConfirmDetails" stripe size="small" style="margin-top: 12px">
          <el-table-column prop="materialCode" label="物料号" min-width="150" />
          <el-table-column prop="planQty" label="计划出库数" width="120" align="center" />
          <el-table-column label="实际出库数" width="160" align="center">
            <template #default="{ row, $index }">
              <el-input-number v-model="row._confirmQty" :min="0" :max="(row.planQty || 0) - (row.actualQty || 0)"
                size="small" controls-position="right" style="width: 140px" />
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">请确认后输入实际出库数量，默认与计划数一致，系统将自动按 FIFO 执行。</span>
            <div>
              <el-button @click="outConfirmVisible = false">取消</el-button>
              <el-button type="primary" :loading="outConfirmSubmitting" @click="handleOutConfirmSubmit">
                确认出库
              </el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 出库单详情对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="outDetailVisible" title="出库单详情"
        width="min(720px, calc(100vw - 32px))" destroy-on-close>
        <div v-if="outDetailData" class="detail-info-grid">
          <div class="detail-info-item">
            <span class="info-label">出库单号</span>
            <span class="info-value">{{ outDetailData.orderNo }}</span>
          </div>
          <div class="detail-info-item">
            <span class="info-label">状态</span>
            <span class="badge" :class="outStatusClass(outDetailData.status)">
              {{ outDetailData.status }}
            </span>
          </div>
          <div class="detail-info-item">
            <span class="info-label">创建时间</span>
            <span class="info-value">{{ outDetailData.createdAt }}</span>
          </div>
        </div>
        <!-- 明细表 -->
        <el-table v-if="outDetailData" :data="outDetailData.details" stripe size="small"
          style="margin-top: 16px">
          <el-table-column prop="materialCode" label="物料号" min-width="140" />
          <el-table-column prop="packCapacity" label="单箱容量" width="100" align="center" />
          <el-table-column prop="planQty" label="计划数" width="100" align="center" />
          <el-table-column prop="actualQty" label="实出数" width="100" align="center" />
        </el-table>
        <!-- 出库看板（一码到底：显示已拣选的入库二维码） -->
        <div v-if="outDetailData && outDetailData.barcodes && outDetailData.barcodes.length > 0"
          class="barcode-gallery">
          <div class="barcode-gallery-title">出库看板（共 {{ outDetailData.barcodes.length }} 个）</div>
          <div class="label-grid">
            <div v-for="bc in outDetailData.barcodes" :key="bc.barcode" class="label-card"
              @click="downloadOutLabel(bc, $event)">
              <div class="label-card-header">
                <el-icon :size="14" class="download-icon"><Download /></el-icon>
              </div>
              <BoxLabel :ref="el => setOutLabelRef(bc.barcode, el)"
                :barcode="bc.barcode"
                :created-at="bc.createdAt" />
              <div class="label-stamp" :class="'stamp-' + stampClass(bc.status)">{{ bc.status }}</div>
            </div>
          </div>
        </div>
        <!-- 出库流水（确认后显示） -->
        <div v-if="outDetailData && outDetailData.histories && outDetailData.histories.length > 0"
          style="margin-top: 16px">
          <div class="barcode-gallery-title">出库流水（共 {{ outDetailData.histories.length }} 条）</div>
          <el-table :data="outDetailData.histories" stripe size="small">
            <el-table-column prop="barcode" label="看板号" min-width="260" show-overflow-tooltip />
            <el-table-column prop="inboundOrderNo" label="来源入库单号" width="200" show-overflow-tooltip />
            <el-table-column prop="deductQty" label="扣减数量" width="90" align="right" />
            <el-table-column prop="createdAt" label="出库时间" width="170" show-overflow-tooltip />
          </el-table>
        </div>
        <template #footer>
          <el-button @click="outDetailVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 修改出库单右侧抽屉 -->
    <el-drawer v-model="outEditVisible" title="修改出库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
>
        <el-alert title="修改后将重新执行整箱拣选，原出库看板标签将被替换。"
          type="warning" show-icon :closable="false" class="draft-alert" />
        <el-form ref="outEditFormRef" :model="outEditForm" label-width="88px">

          <!-- 物料管理：已选摘要 + 弹窗入口（出库无供应商限制） -->
          <el-form-item label="物料管理">
            <div class="material-summary">
              <div v-if="materialCount(outEditForm.details) === 0" class="summary-empty">
                尚未选择物料，请点击下方按钮添加
              </div>
              <el-table v-else :data="outEditForm.details.filter(d => d.materialCode)" size="small" max-height="240">
                <el-table-column type="index" label="#" width="40" />
                <el-table-column prop="materialCode" label="物料号" width="140" />
                <el-table-column label="器具类型" width="100">
                  <template #default="{ row }">{{ row.packType || '—' }}</template>
                </el-table-column>
                <el-table-column label="单箱容量" width="80" align="right">
                  <template #default="{ row }">{{ row.packCapacity || '—' }}</template>
                </el-table-column>
                <el-table-column label="出库箱数" width="90" align="right">
                  <template #default="{ row }">{{ row.boxCount }}</template>
                </el-table-column>
                <el-table-column label="总件数" width="90" align="right">
                  <template #default="{ row }">{{ row.planQty || (row.boxCount * row.packCapacity) }}</template>
                </el-table-column>
              </el-table>
              <div class="summary-footer">
                <span>
                  共 <b>{{ materialCount(outEditForm.details) }}</b> 种物料 /
                  <b>{{ outEditTotalBoxes }}</b> 箱 /
                  <b>{{ outEditTotalQty }}</b> 件
                </span>
                <el-button type="primary" size="small" @click="openMaterialPicker('outEdit')">
                  <el-icon :size="14"><Plus /></el-icon>
                  管理物料明细
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">修改后需重新确认出库。合计 {{ outEditTotalBoxes }} 箱，{{ outEditTotalQty }} 件</span>
            <div>
              <el-button @click="outEditVisible = false">取消</el-button>
              <el-button type="primary" :loading="outEditSubmitting" @click="handleOutEditSubmit">保存修改</el-button>
            </div>
          </div>
        </template>
    </el-drawer>

    <!-- 物料选择弹窗（Teleport to body，独立于抽屉） -->
    <MaterialPickerDialog
      v-model="pickerVisible"
      v-model:details="pickerDetails"
      :supplier-codes="pickerSupplierCodes"
      :title="pickerTitle"
      :type="pickerType"
    />

    <!-- 入库流水查询对话框 -->
    <Teleport to="body">
      <el-dialog v-model="inboundFlowVisible" title="入库流水查询"
        width="min(900px, calc(100vw - 32px))" @opened="loadInboundFlow">
        <div class="toolbar" style="margin-bottom: 12px">
          <el-input v-model="inboundFlowOrderNo" placeholder="入库单号（模糊）" clearable
            size="small" style="width: 180px" @keyup.enter="loadInboundFlow" />
          <el-select v-model="inboundFlowMaterial" placeholder="物料号"
            size="small" filterable remote clearable style="width: 180px"
            :remote-method="(q) => searchFlowMaterials(q, 'inbound')"
            :loading="flowMaterialLoading.inbound"
            @focus="searchFlowMaterials('', 'inbound')"
            @change="loadInboundFlow">
            <el-option v-for="m in flowMaterialOptions.inbound" :key="m.materialCode"
              :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
          </el-select>
          <el-button type="primary" size="small" @click="loadInboundFlow">查询</el-button>
        </div>
        <el-table :data="inboundFlowList" stripe size="small" v-loading="inboundFlowLoading"
          empty-text="暂无入库流水记录">
          <el-table-column label="入库单号" width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ getInboundOrderNo(row.inboundId) }}</template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料号" width="130" />
          <el-table-column prop="barcode" label="看板号" min-width="260" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <span class="badge" :class="row.status === '在库' ? 'badge-success' : row.status === '已出库' ? 'badge-default' : 'badge-warn'">{{ row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remainingQty" label="剩余数量" width="90" align="right" />
          <el-table-column prop="createdAt" label="生成时间" width="170" show-overflow-tooltip />
        </el-table>
        <template #footer><el-button @click="inboundFlowVisible = false">关闭</el-button></template>
      </el-dialog>
    </Teleport>

    <!-- 出库流水查询对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="historyVisible" title="出库批次流水查询"
        width="min(900px, calc(100vw - 32px))" @opened="loadHistories">
        <div class="toolbar" style="margin-bottom: 12px">
          <el-input v-model="historyOrderNo" placeholder="出库单号（模糊）" clearable
            size="small" style="width: 180px" @keyup.enter="loadHistories" />
          <el-select v-model="historyMaterial" placeholder="物料号"
            size="small" filterable remote clearable style="width: 180px"
            :remote-method="(q) => searchFlowMaterials(q, 'outbound')"
            :loading="flowMaterialLoading.outbound"
            @focus="searchFlowMaterials('', 'outbound')"
            @change="loadHistories">
            <el-option v-for="m in flowMaterialOptions.outbound" :key="m.materialCode"
              :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
          </el-select>
          <el-button type="primary" size="small" @click="loadHistories">查询</el-button>
        </div>
        <el-table :data="historyList" stripe size="small" v-loading="historyLoading"
          empty-text="暂无出库流水记录">
          <el-table-column prop="outboundOrderNo" label="出库单号" width="200" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料号" width="130" />
          <el-table-column prop="inboundOrderNo" label="来源入库单号" width="200" show-overflow-tooltip />
          <el-table-column prop="barcode" label="看板号" min-width="260" show-overflow-tooltip />
          <el-table-column prop="deductQty" label="扣减数量" width="90" align="right" />
          <el-table-column prop="createdAt" label="出库时间" width="170" show-overflow-tooltip />
        </el-table>
        <template #footer>
          <el-button @click="historyVisible = false">关闭</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Printer, Download } from '@element-plus/icons-vue'
import { getInboundOrders, createInbound, updateInbound, confirmInbound, getInboundDetail, getInboundFlow, deleteInbound } from '@/api/inbound'
import { getOutboundOrders, createOutbound, updateOutbound, deleteOutbound, confirmOutbound, getOutboundDetail, getOutboundHistories } from '@/api/outbound'
import { getSuppliers } from '@/api/suppliers'
import { getMaterials } from '@/api/materials'
import { getAppliances } from '@/api/appliances'
import { triggerPredict } from '@/api/ai'
import { useUserStore } from '@/stores/user'
import QRCode from '@/components/QRCode.vue'
import BoxLabel from '@/components/BoxLabel.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import QRCodeLib from 'qrcode'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('inbound')

const pendingSuppliers = ref([]) // 待确认的多选供应商

// ==================== 物料选择弹窗 ====================
const pickerVisible = ref(false)
const pickerDetails = ref([])       // 当前编辑的 details 引用
const pickerSupplierCodes = ref([]) // 当前表单的供应商列表
const pickerTitle = ref('')
const pickerType = ref('inbound')

/** 统计明细中已填物料的行数 */
function materialCount(details) {
  return details.filter(d => d.materialCode).length
}

/** 打开物料选择弹窗 */
function openMaterialPicker(formType) {
  const formMap = {
    inbound: inboundForm,
    editInbound: editForm,
    outbound: outboundForm,
    outEdit: outEditForm
  }
  const form = formMap[formType]
  if (!form) return
  pickerDetails.value = form.details
  pickerType.value = formType.startsWith('inbound') || formType.startsWith('edit')
    ? 'inbound' : 'outbound'
  pickerSupplierCodes.value = pickerType.value === 'inbound'
    ? (form.selectedSuppliers || [form.supplierCode]).filter(Boolean)
    : []
  pickerTitle.value = pickerType.value === 'inbound'
    ? '选择入库物料' : '选择出库物料'
  pickerVisible.value = true
}

// ==================== 供应商多选计算属性 ====================
/** 未被选中的供应商列表（el-select 自带搜索过滤） */
const availableSuppliers = computed(() =>
  supplierOptions.value.filter(s => !inboundForm.selectedSuppliers.includes(s.supplierCode))
)

// ==================== 汇总计算 ====================
const inboundTotalBoxes = computed(() =>
  inboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const inboundTotalQty = computed(() =>
  inboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.planQty > 0 ? d.planQty : (d.boxCount || 0) * (d.packCapacity || 0)) : 0), 0)
)
const editInboundTotalBoxes = computed(() =>
  editForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const editInboundTotalQty = computed(() =>
  editForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.planQty > 0 ? d.planQty : (d.boxCount || 0) * (d.packCapacity || 0)) : 0), 0)
)
const outTotalBoxes = computed(() =>
  outboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const outTotalQty = computed(() =>
  outboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.planQty > 0 ? d.planQty : (d.boxCount || 0) * (d.packCapacity || 0)) : 0), 0)
)
const outEditTotalBoxes = computed(() =>
  outEditForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const outEditTotalQty = computed(() =>
  outEditForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.planQty > 0 ? d.planQty : (d.boxCount || 0) * (d.packCapacity || 0)) : 0), 0)
)

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
  selectedSuppliers: [],
  details: [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
})
const inboundRules = {
  supplierCode: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

// ==================== 修改入库单 ====================
const editVisible = ref(false)
const editFormRef = ref(null)
const editTarget = ref(null)
const editSubmitting = ref(false)
const editForm = reactive({
  supplierCode: '',
  details: [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
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
// ==================== 打印看板 ====================

/**
 * 批量打印出入库单中的所有看板标签，直接调起系统打印机。
 * 使用隐藏 iframe + 预生成二维码，图片加载完成后自动触发打印。
 */
async function printBarcodes(row) {
  try {
    const detail = activeTab.value === 'inbound'
      ? await getInboundDetail(row.id)
      : await getOutboundDetail(row.id)
    const barcodes = detail?.barcodes || []
    if (barcodes.length === 0) {
      ElMessage.warning('该单据无看板可打印')
      return
    }
    const orderNo = detail.orderNo || row.orderNo || ''
    const typeLabel = activeTab.value === 'inbound' ? '入库' : '出库'

    // 预生成所有二维码 data URL
    const qrUrls = await Promise.all(barcodes.map(bc =>
      QRCodeLib.toDataURL(bc.barcode, { width: 120, margin: 1, errorCorrectionLevel: 'M' })
        .catch(() => '')
    ))

    // 批量获取器具类型（与屏幕看板一致）
    const matSupSet = new Set()
    barcodes.forEach(bc => {
      const parts = (bc.barcode || '').split('|')
      if (parts[1] && parts[2]) matSupSet.add(parts[1] + '|' + parts[2])
    })
    const packTypeMap = {}
    if (matSupSet.size > 0) {
      try {
        const appData = await getAppliances({ page: 1, size: 200 })
        const apps = appData.records || []
        for (const key of matSupSet) {
          const [mat, sup] = key.split('|')
          const app = apps.find(a => a.materialCode === mat && a.supplierCode === sup)
          packTypeMap[key] = app?.packType || '—'
        }
      } catch { /* */ }
    }

    // 构建卡片 HTML（与 BoxLabel 组件完全一致的布局）
    let cardsHtml = ''
    for (let i = 0; i < barcodes.length; i++) {
      const bc = barcodes[i]
      const parts = (bc.barcode || '').split('|')
      const materialCode = h(parts[1] || '—')
      const supplierCode = h(parts[2] || '—')
      const packCapacity = parts[4] || '—'
      const packType = h(packTypeMap[parts[1] + '|' + parts[2]] || '—')
      const createdAt = bc.createdAt ? bc.createdAt.substring(0, 10) : '—'
      const barcodeFull = h(bc.barcode || '—')
      cardsHtml += `<div class="kc">
        <div class="kq"><img src="${qrUrls[i]}" width="100" height="100" onerror="this.style.display='none'"></div>
        <table class="kt">
          <tr><td class="kl">物料号</td><td class="kv">${materialCode}</td></tr>
          <tr><td class="kl">供应商</td><td class="kv">${supplierCode}</td></tr>
          <tr><td class="kl">器具</td><td class="kv">${packType}</td></tr>
          <tr><td class="kl">日期</td><td class="kv">${createdAt}</td></tr>
          <tr><td class="kl">数量</td><td class="kv">${packCapacity} 件/箱</td></tr>
          <tr><td class="kl">看板号</td><td class="kv kn">${barcodeFull}</td></tr>
        </table>
      </div>`
    }

    const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>看板_${orderNo}</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:"Microsoft YaHei","PingFang SC",sans-serif;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.kc{background:#fff;border:2px solid #2c3e50;border-radius:4px;display:flex;align-items:center;gap:12px;padding:10px;width:420px;margin:0 auto;page-break-after:always}
.kc:last-child{page-break-after:auto}
.kq{width:100px;height:100px;flex-shrink:0;border:1px solid #eee;border-radius:3px;padding:3px;background:#fff;display:flex;align-items:center;justify-content:center}
.kq img{width:92px;height:92px}
.kt{flex:1;min-width:0;border-collapse:collapse;font-size:12px}
.kt td{padding:2px 6px;vertical-align:middle}
.kl{color:#888;font-size:11px;white-space:nowrap;width:48px}
.kv{color:#222;font-weight:600;font-size:12px}
.kn{font-size:10px;font-family:"Courier New",monospace;word-break:break-all}
@media print{@page{size:A4;margin:8mm}}
</style></head><body>${cardsHtml}</body></html>`

    // 使用隐藏 iframe 方式打印，避免弹窗被拦截且保证图片加载完成
    const oldFrame = document.getElementById('print-frame')
    if (oldFrame) oldFrame.remove()
    const iframe = document.createElement('iframe')
    iframe.id = 'print-frame'
    iframe.style.cssText = 'position:fixed;top:-9999px;left:-9999px;width:0;height:0;border:none'
    document.body.appendChild(iframe)

    const iframeDoc = iframe.contentDocument || iframe.contentWindow.document
    iframeDoc.open()
    iframeDoc.write(html)
    iframeDoc.close()

    // 等待图片全部加载完毕后再打印
    const images = iframeDoc.querySelectorAll('img')
    let loadedCount = 0
    const totalImages = images.length

    function tryPrint() {
      loadedCount++
      if (loadedCount >= totalImages) {
        setTimeout(() => {
          iframe.contentWindow.focus()
          iframe.contentWindow.print()
          // 监听打印完成事件（部分浏览器支持）
          iframe.contentWindow.onafterprint = () => {
            setTimeout(() => {
              if (document.getElementById('print-frame')) {
                document.getElementById('print-frame').remove()
              }
            }, 500)
          }
          // 兜底：10秒后清理 iframe
          setTimeout(() => {
            if (document.getElementById('print-frame')) {
              document.getElementById('print-frame').remove()
            }
          }, 10000)
        }, 200)
      }
    }

    if (totalImages === 0) {
      tryPrint()
    } else {
      images.forEach(img => {
        if (img.complete) {
          tryPrint()
        } else {
          img.onload = tryPrint
          img.onerror = tryPrint
        }
      })
    }
  } catch (err) {
    ElMessage.error('加载看板数据失败')
  }
}
function h(s) { return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;') }

// ==================== 计算属性 ====================
const pendingCount = computed(() => inboundList.value.filter(row => row.status !== '已完成').length)
const completedCount = computed(() => inboundList.value.filter(row => row.status === '已完成').length)

onMounted(() => {
  loadOrders()
  loadSuppliers()
  loadOutboundOrders()
  applyAiInboundDraft()
})

watch(
  () => route.query,
  () => applyAiInboundDraft()
)

// 切换 tab 时加载对应数据
watch(activeTab, (tab) => {
  if (tab === 'outbound') loadOutboundOrders()
})

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

/** 获取供应商名称 */
function getSupplierName(code) {
  const s = supplierOptions.value.find(s => s.supplierCode === code)
  return s ? `${s.supplierName} (${s.supplierCode})` : code
}

/** 确认添加多选的供应商标签 */
function confirmAddSuppliers() {
  if (pendingSuppliers.value.length === 0) return
  let added = 0
  for (const code of pendingSuppliers.value) {
    if (!inboundForm.selectedSuppliers.includes(code)) {
      inboundForm.selectedSuppliers.push(code)
      added++
    }
  }
  pendingSuppliers.value = []
  if (added > 0) {
    ElMessage.success(`已添加 ${added} 家供应商`)
  }
}

/** 添加单个供应商（保留兼容） */
function addSupplier(code) {
  if (!inboundForm.selectedSuppliers.includes(code)) {
    inboundForm.selectedSuppliers.push(code)
    pendingSuppliers.value = []
  }
}

/** 移除供应商标签 */
function removeSupplier(code) {
  const idx = inboundForm.selectedSuppliers.indexOf(code)
  if (idx >= 0) inboundForm.selectedSuppliers.splice(idx, 1)
}

function openInboundDialog() {
  isAiDraft.value = false
  inboundForm.selectedSuppliers = []
  pendingSuppliers.value = []
  inboundForm.details = [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
  dialogVisible.value = true
}

async function applyAiInboundDraft() {
  const materialCode = String(route.query.materialCode || '').trim()
  const suggestedQty = Number(route.query.suggestedQty || 0)
  if (!materialCode || suggestedQty <= 0) return

  isAiDraft.value = true
  activeTab.value = 'inbound'
  router.replace({ path: route.path, query: {} })

  // 查找物料默认供应商和包装容量
  let supplierCode = ''
  let packCapacity = 20
  let packType = ''
  try {
    // 查物料默认供应商
    const matData = await getMaterials({ page: 1, size: 100, keyword: materialCode })
    const mat = (matData.records || []).find(r => r.materialCode === materialCode)
    if (mat) supplierCode = mat.supplierCode || ''

    // 查器具包装容量
    if (supplierCode) {
      try {
        const appData = await getAppliances({ page: 1, size: 10, keyword: materialCode })
        const app = (appData.records || []).find(r =>
          r.materialCode === materialCode && r.supplierCode === supplierCode)
        if (app) {
          packCapacity = app.packCapacity || 20
          packType = app.packType || ''
        }
      } catch { /* */ }
    }
  } catch { /* */ }

  // 按建议量计算箱数（向上取整）
  const boxCount = Math.max(1, Math.ceil(suggestedQty / packCapacity))

  inboundForm.selectedSuppliers = supplierCode ? [supplierCode] : []
  inboundForm.details = [{
    materialCode,
    packType,
    packCapacity,
    boxCount,
    planQty: suggestedQty
  }]
  dialogVisible.value = true

  if (supplierCode) {
    ElMessage.success(`AI 建议补货 ${suggestedQty} 件，已自动填入 ${boxCount} 箱（${packCapacity}件/箱）`)
  } else {
    ElMessage.info(`AI 建议补货 ${suggestedQty} 件，请手动添加供应商`)
  }
}

async function handleCreate() {
  // 手动校验必填项
  if (inboundForm.selectedSuppliers.length === 0) {
    ElMessage.warning('请至少添加一个供应商')
    return
  }
  const invalidDetail = inboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.boxCount || item.boxCount < 1 || !item.packCapacity || item.packCapacity < 1
  )
  if (invalidDetail) {
    if (!invalidDetail.materialCode?.trim()) ElMessage.warning('请选择每行物料')
    else if (!invalidDetail.packCapacity || invalidDetail.packCapacity < 1) ElMessage.warning('物料 ' + invalidDetail.materialCode + ' 未配置器具容量，请先到器具管理页面配置')
    else ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createInbound({
      supplierCode: inboundForm.selectedSuppliers[0],
      details: inboundForm.details.map(d => ({ materialCode: d.materialCode, supplierCode: d.supplierCode || '', boxCount: d.boxCount, planQty: d.planQty || (d.boxCount * d.packCapacity) }))
    })
    // 后台异步刷新 AI 报告，确保建议反映最新库存
    for (const d of inboundForm.details) {
      if (d.materialCode) triggerPredict(d.materialCode).catch(() => {})
    }
    ElMessage.success('入库单创建成功')
    dialogVisible.value = false
    loadOrders()
  } catch (err) {
    ElMessage.error(err.message || '创建入库单失败')
  }
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
    // 库存已变化，异步刷新 AI 报告
    confirmDetails.value.forEach(d => triggerPredict(d.materialCode).catch(() => {}))
    ElMessage.success('入库确认成功')
    confirmVisible.value = false
    loadOrders()
  } catch { /* */ } finally {
    confirmSubmitting.value = false
  }
}

/**
 * 编辑对话框：供应商切换时清空物料明细。
 */
function onEditSupplierChange() {
  editForm.details.forEach(item => { item.materialCode = ''; item.packType = ''; item.packCapacity = 0; item.planQty = 0 })
}

async function openEditDialog(row) {
  editTarget.value = row
  editSubmitting.value = false
  try {
    const data = await getInboundDetail(row.id)
    editForm.supplierCode = data.supplierCode || ''
    editForm.details = (data.details || []).map(d => ({
      materialCode: d.materialCode || '',
      packType: '',
      packCapacity: d.packCapacity || 0,
      boxCount: (d.packCapacity > 0) ? Math.max(1, Math.ceil((d.planQty || 0) / d.packCapacity)) : 1,
      planQty: d.planQty || ((d.packCapacity > 0) ? Math.max(1, Math.ceil((d.planQty || 0) / d.packCapacity)) * d.packCapacity : 0)
    }))
    if (editForm.details.length === 0) {
      editForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
    }
    // 批量获取器具类型（仅用于摘要展示，明细编辑在弹窗中进行）
    const materialCodes = [...new Set(editForm.details.map(d => d.materialCode).filter(Boolean))]
    if (materialCodes.length > 0) {
      try {
        const appData = await getAppliances({ page: 1, size: 100 })
        const apps = appData.records || []
        for (const d of editForm.details) {
          if (d.materialCode) {
            const app = apps.find(a => a.materialCode === d.materialCode && a.supplierCode === editForm.supplierCode)
            if (app) d.packType = app.packType || ''
          }
        }
      } catch { /* 获取失败也不影响主流程 */ }
    }
    editVisible.value = true
  } catch {
    ElMessage.error('加载入库单详情失败')
  }
}

async function handleEditSubmit() {
  if (!editForm.supplierCode) {
    ElMessage.warning('请选择供应商')
    return
  }
  const invalid = editForm.details.find(d =>
    !d.materialCode?.trim() || !d.boxCount || d.boxCount < 1 || !d.packCapacity || d.packCapacity < 1
  )
  if (invalid) {
    if (!invalid.materialCode?.trim()) ElMessage.warning('请选择每行物料')
    else if (!invalid.packCapacity || invalid.packCapacity < 1) ElMessage.warning('物料 ' + invalid.materialCode + ' 未配置器具容量')
    else ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  editSubmitting.value = true
  try {
    await updateInbound(editTarget.value.id, {
      supplierCode: editForm.supplierCode,
      details: editForm.details.map(d => ({ materialCode: d.materialCode, supplierCode: d.supplierCode || '', boxCount: d.boxCount, planQty: d.planQty || (d.boxCount * d.packCapacity) }))
    })
    ElMessage.success('入库单修改成功')
    editVisible.value = false
    loadOrders()
  } catch (err) {
    ElMessage.error(err.message || '修改入库单失败')
  } finally {
    editSubmitting.value = false
  }
}

// ==================== 标签下载 ====================
/** 存储每个标签组件的引用，用于导出完整标签图片 */
const labelRefs = {}

function setLabelRef(barcode, el) {
  if (el) labelRefs[barcode] = el
}

/**
 * 从二维码字符串中提取用于文件名的物料号和箱号。
 */
function parseBarcodeForFilename(str) {
  const parts = (str || '').split('|')
  return {
    materialCode: parts[1] || 'UNKNOWN',
    boxSeq: parts[6] || '1',
  }
}

/**
 * 下载入库箱单标签 PNG 图片。
 * 命名规则: 入库单号_箱N.png
 */
function downloadLabel(bc, event) {
  event.stopPropagation()
  const component = labelRefs[bc.barcode]
  if (!component) return
  const canvas = component.getCanvas()
  if (!canvas) return

  const info = parseBarcodeForFilename(bc.barcode)
  const orderNo = detailData.value?.orderNo || 'RK'
  canvas.toBlob((blob) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${orderNo}_箱${info.boxSeq}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('标签已下载')
  }, 'image/png')
}

// ==================== 出库标签下载 ====================
/** 存储每个出库标签组件的引用，用于导出完整标签图片 */
const outLabelRefs = {}

function setOutLabelRef(barcode, el) {
  if (el) outLabelRefs[barcode] = el
}

/**
 * 下载出库箱单标签 PNG 图片。
 * 命名规则: 出库单号_箱N.png
 * @param {Object} bc Barcode 对象 { barcode, status, ... }
 */
function downloadOutLabel(bc, event) {
  event.stopPropagation()
  const component = outLabelRefs[bc.barcode]
  if (!component) return
  const canvas = component.getCanvas()
  if (!canvas) return

  // 从二维码字符串解析箱号: OUT|MAT|CK20260615...|15|90|15|3 → boxSeq=7
  const parts = (bc.barcode || '').split('|')
  const boxSeq = parts.length >= 7 ? parts[6] : '1'
  const orderNo = outDetailData.value?.orderNo || 'CK'
  canvas.toBlob((blob) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${orderNo}_箱${boxSeq}.png`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('标签已下载')
  }, 'image/png')
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

// ==================== 入库单删除 ====================
async function handleInboundDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除入库单 ${row.orderNo}？关联条码和明细将一并清除。`, '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await deleteInbound(row.id)
    ElMessage.success('入库单已删除')
    loadOrders()
  } catch { /* 取消或失败 */ }
}

// ==================== 出库管理 ====================
const outboundList = ref([])
const outboundLoading = ref(false)

// 统计
const outPendingCount = computed(() => outboundList.value.filter(r => r.status !== '已完成').length)
const outCompletedCount = computed(() => outboundList.value.filter(r => r.status === '已完成').length)

function outStatusClass(status) {
  if (status === '已完成') return 'badge-success'
  if (status === '部分完成') return 'badge-warn'
  return 'badge-default'
}
function stampClass(s) {
  if (s === '在库') return 'in'
  if (s === '已出库') return 'out'
  if (s === '待入库' || s === '待出库') return 'pending'
  return 'default'
}

async function loadOutboundOrders() {
  outboundLoading.value = true
  try {
    const data = await getOutboundOrders({ page: 1, size: 50 })
    outboundList.value = data.records || []
  } catch { /* */ } finally {
    outboundLoading.value = false
  }
}

// ==================== 新建出库单 ====================
const outDialogVisible = ref(false)
const outFormRef = ref(null)

const outboundForm = reactive({
  details: [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
})

function openOutboundDialog() {
  outboundForm.details = [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
  outDialogVisible.value = true
}

async function handleOutCreate() {
  const invalid = outboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.boxCount || item.boxCount < 1 || !item.packCapacity || item.packCapacity < 1
  )
  if (invalid) {
    if (!invalid.materialCode?.trim()) ElMessage.warning('请选择每行物料')
    else if (!invalid.packCapacity || invalid.packCapacity < 1) ElMessage.warning('物料 ' + invalid.materialCode + ' 未配置器具容量，请先到器具管理页面配置')
    else ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createOutbound({ details: outboundForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount, planQty: d.planQty || (d.boxCount * d.packCapacity) })) })
    ElMessage.success('出库单创建成功')
    outDialogVisible.value = false
    loadOutboundOrders()
  } catch (err) {
    ElMessage.error(err.message || '创建出库单失败')
  }
}

// ==================== 修改出库单 ====================
const outEditVisible = ref(false)
const outEditFormRef = ref(null)
const outEditTarget = ref(null)
const outEditSubmitting = ref(false)
const outEditForm = reactive({
  details: [{ materialCode: '', supplierCode: '', packType: '', packCapacity: 0, boxCount: 1, planQty: 0 }]
})

async function openOutEditDialog(row) {
  outEditTarget.value = row
  outEditSubmitting.value = false
  try {
    const data = await getOutboundDetail(row.id)
    outEditForm.details = (data.details || []).map(d => ({
      materialCode: d.materialCode || '',
      packType: '',
      packCapacity: d.packCapacity || 0,
      boxCount: (d.packCapacity > 0) ? Math.max(1, Math.ceil((d.planQty || 0) / d.packCapacity)) : 1,
      planQty: d.planQty || ((d.packCapacity > 0) ? Math.max(1, Math.ceil((d.planQty || 0) / d.packCapacity)) * d.packCapacity : 0)
    }))
    if (outEditForm.details.length === 0) {
      outEditForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
    }
    // 批量获取器具类型（仅用于摘要展示）
    const materialCodes = [...new Set(outEditForm.details.map(d => d.materialCode).filter(Boolean))]
    if (materialCodes.length > 0) {
      try {
        const appData = await getAppliances({ page: 1, size: 100 })
        const apps = appData.records || []
        for (const d of outEditForm.details) {
          if (d.materialCode) {
            const app = apps.find(a => a.materialCode === d.materialCode)
            if (app) d.packType = app.packType || ''
          }
        }
      } catch { /* */ }
    }
    outEditVisible.value = true
  } catch { ElMessage.error('加载出库单详情失败') }
}

async function handleOutEditSubmit() {
  const invalid = outEditForm.details.find(d =>
    !d.materialCode?.trim() || !d.boxCount || d.boxCount < 1 || !d.packCapacity || d.packCapacity < 1
  )
  if (invalid) {
    if (!invalid.materialCode?.trim()) ElMessage.warning('请选择每行物料')
    else if (!invalid.packCapacity || invalid.packCapacity < 1) ElMessage.warning('物料 ' + invalid.materialCode + ' 未配置器具容量')
    else ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  outEditSubmitting.value = true
  try {
    await updateOutbound(outEditTarget.value.id, { details: outEditForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount, planQty: d.planQty || (d.boxCount * d.packCapacity) })) })
    ElMessage.success('出库单修改成功')
    outEditVisible.value = false
    loadOutboundOrders()
  } catch (err) { ElMessage.error(err.message || '修改出库单失败') }
  finally { outEditSubmitting.value = false }
}

async function handleOutDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除出库单 ${row.orderNo}？已拣库存将退回。`, '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await deleteOutbound(row.id)
    ElMessage.success('出库单已删除')
    loadOutboundOrders()
  } catch { /* 取消或失败 */ }
}

// ==================== 确认出库 ====================
const outConfirmVisible = ref(false)
const outConfirmTarget = ref(null)
const outConfirmDetails = ref([])
const outConfirmSubmitting = ref(false)

async function openOutConfirmDialog(row) {
  outConfirmTarget.value = row
  outConfirmSubmitting.value = false
  try {
    const data = await getOutboundDetail(row.id)
    // 为每行添加 _confirmQty 字段，默认等于待出数量
    outConfirmDetails.value = (data.details || []).map(d => ({
      ...d,
      _confirmQty: Math.max(0, (d.planQty || 0) - (d.actualQty || 0))
    }))
  } catch {
    outConfirmDetails.value = []
  }
  outConfirmVisible.value = true
}

/**
 * 将二维码输入框中的值添加为二维码标签。
 * 支持扫码枪回车输入和手动粘贴（按逗号/空格/换行分割）。
 */
async function handleOutConfirmSubmit() {
  const invalid = outConfirmDetails.value.find(d => d._confirmQty < 0)
  if (invalid) {
    ElMessage.warning('出库数量不能小于 0')
    return
  }
  if (!outConfirmDetails.value.some(d => d._confirmQty > 0)) {
    ElMessage.warning('请至少填写一条明细的本次出库数')
    return
  }

  outConfirmSubmitting.value = true
  try {
    const details = outConfirmDetails.value
      .filter(d => d._confirmQty > 0)
      .map(d => ({
        detailId: d.id,
        actualQty: d._confirmQty
      }))
    await confirmOutbound(outConfirmTarget.value.id, { details })
    ElMessage.success('出库确认成功')
    outConfirmVisible.value = false
    loadOutboundOrders()
  } catch (err) {
    ElMessage.error(err.message || '出库确认失败')
  } finally {
    outConfirmSubmitting.value = false
  }
}

// ==================== 出库单详情 ====================
const outDetailVisible = ref(false)
const outDetailData = ref(null)

async function openOutDetailDialog(row) {
  outDetailData.value = null
  outDetailVisible.value = true
  try {
    outDetailData.value = await getOutboundDetail(row.id)
  } catch {
    outDetailVisible.value = false
  }
}

// ==================== 流水查询 — 物料下拉搜索 ====================
const flowMaterialOptions = reactive({ inbound: [], outbound: [] })
const flowMaterialLoading = reactive({ inbound: false, outbound: false })

/** 流水查询弹窗中远程搜索物料下拉选项 */
async function searchFlowMaterials(query, type) {
  flowMaterialLoading[type] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    flowMaterialOptions[type] = data.records || []
  } catch {
    flowMaterialOptions[type] = []
  } finally {
    flowMaterialLoading[type] = false
  }
}

// ==================== 入库流水查询 ====================
const inboundFlowVisible = ref(false)
const inboundFlowLoading = ref(false)
const inboundFlowList = ref([])
const inboundFlowOrderNo = ref('')
const inboundFlowMaterial = ref('')
const inboundOrderNoMap = ref({})

function openInboundFlowDialog() {
  inboundFlowOrderNo.value = ''
  inboundFlowMaterial.value = ''
  inboundFlowList.value = []
  inboundFlowVisible.value = true
}

async function loadInboundFlow() {
  ElMessage.info('入库流水查询中...')
  inboundFlowLoading.value = true
  try {
    const params = { page: 1, size: 200 }
    const on = inboundFlowOrderNo.value?.trim()
    const mc = inboundFlowMaterial.value?.trim()
    if (on) params.orderNo = on
    if (mc) params.materialCode = mc
    const data = await getInboundFlow(params)
    inboundFlowList.value = data.records || []
    const ids = [...new Set(inboundFlowList.value.map(r => r.inboundId).filter(Boolean))]
    for (const id of ids) {
      if (!inboundOrderNoMap.value[id]) {
        try { const d = await getInboundDetail(id); inboundOrderNoMap.value[id] = d.orderNo } catch { inboundOrderNoMap.value[id] = '—' }
      }
    }
  } catch { inboundFlowList.value = [] } finally { inboundFlowLoading.value = false }
}
function getInboundOrderNo(inboundId) { return inboundOrderNoMap.value[inboundId] || '加载中...' }

// ==================== 出库流水查询 ====================
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historyOrderNo = ref('')
const historyMaterial = ref('')

function openHistoryDialog() {
  historyOrderNo.value = ''
  historyMaterial.value = ''
  historyList.value = []
  historyVisible.value = true
}

async function loadHistories() {
  console.log('[loadHistories] 触发, orderNo=' + historyOrderNo.value + ' materialCode=' + historyMaterial.value)
  historyLoading.value = true
  try {
    const params = { page: 1, size: 50 }
    const on = historyOrderNo.value?.trim()
    const mc = historyMaterial.value?.trim()
    if (on) params.orderNo = on
    if (mc) params.materialCode = mc
    console.log('[loadHistories] 请求参数:', JSON.stringify(params))
    const data = await getOutboundHistories(params)
    console.log('[loadHistories] 响应:', data.records?.length + '条记录')
    historyList.value = data.records || []
  } catch (err) {
    console.error('[loadHistories] 失败:', err)
    historyList.value = []
    ElMessage.error('查询失败: ' + (err?.message || '未知错误'))
  } finally {
    historyLoading.value = false
  }
}

</script>

<style scoped>
/* ==================== 布局 ==================== */
.history-stats {
  display: flex;
  gap: 24px;
  padding: 10px 16px;
  margin-bottom: 14px;
  background: #f7f9fc;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}
.history-stats b {
  color: var(--wms-primary);
  margin-left: 4px;
}
.inbound-toolbar :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.draft-alert {
  margin-bottom: 16px;
}
/* 物料摘要面板（抽屉内） */
.material-summary {
  width: 100%;
}
.summary-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  background: #fafafa;
  border: 1px dashed var(--border-light);
  border-radius: 4px;
}
.summary-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f0f5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.summary-footer b {
  color: var(--wms-primary);
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
.badge-warn    { background: #fdf6ec; color: #e6a23c; }
.badge-default { background: #f4f4f5; color: #909399; }

/* ==================== 标签画廊（网格布局，可点击下载） ==================== */
.barcode-gallery {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
.barcode-gallery-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}
/* 标签网格（无内部滚动，由全局 el-dialog__body 统一滚动） */
.label-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
}
.label-card {
  position: relative;
  cursor: pointer;
  transition: box-shadow 0.15s;
  border-radius: 4px;
}
.label-card:hover {
  box-shadow: 0 1px 8px rgba(64, 158, 255, 0.18);
}
.label-card-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 4px 6px;
}
.download-icon {
  color: var(--text-secondary);
  flex-shrink: 0;
}
.label-card:hover .download-icon {
  color: var(--wms-primary);
}
.barcode-status-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 2px;
  white-space: nowrap;
}
.tag-in-stock { background: #f0f9eb; color: #67c23a; }
.tag-pending { background: #fdf6ec; color: #e6a23c; }
.tag-outbound { background: #f4f4f5; color: #909399; }

/* ==================== 看板状态印章 ==================== */
.label-stamp {
  display: inline-block;
  font-size: 11px; font-weight: 600;
  padding: 1px 8px; margin-top: 6px;
  border-radius: 2px;
  border: 1.5px solid; line-height: 1.5;
  opacity: 0.8;
}
.stamp-in {
  color: #67c23a; border-color: #67c23a;
  background: rgba(103,194,58,0.06);
}
.stamp-out {
  color: #909399; border-color: #909399;
  background: rgba(144,147,153,0.06);
}
.stamp-pending {
  color: #e6a23c; border-color: #e6a23c;
  background: rgba(230,162,60,0.06);
}
.stamp-default {
  color: #909399; border-color: #909399;
  background: rgba(144,147,153,0.04);
}

/* ==================== 打印预览 ==================== */
/* ==================== 响应式 ==================== */
@media (max-width: 760px) {
  .history-stats,
  .dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-stats {
    flex-wrap: wrap;
  }
  .detail-info-grid {
    grid-template-columns: 1fr;
  }
}

/* ==================== 供应商多选标签 ==================== */
.supplier-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.supplier-pick-group {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.supplier-pick-group :deep(.el-select__wrapper) {
  min-width: 180px;
}
</style>

