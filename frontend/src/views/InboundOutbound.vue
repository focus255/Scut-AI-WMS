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
          <div class="inbound-summary">
            <div>
              <div class="summary-title">出库单流转</div>
              <div class="summary-desc">创建出库单后扫码确认，系统按先进先出规则校验条码并扣减库存。</div>
            </div>
            <div class="summary-stats">
              <span>待出库 {{ outPendingCount }}</span>
              <span>已完成 {{ outCompletedCount }}</span>
            </div>
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
            <el-table-column label="操作" width="280" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="primary" link size="small"
                  @click.stop="openOutEditDialog(row)">
                  编辑
                </el-button>
                <el-button v-if="row.status === '未出库'" type="danger" link size="small"
                  @click.stop="handleOutDelete(row)">
                  删除
                </el-button>
                <el-button v-if="row.status !== '已完成'" type="success" link size="small"
                  @click.stop="openOutConfirmDialog(row)">
                  确认出库
                </el-button>
                <span v-if="row.status === '已完成'" class="muted-text">无需操作</span>
              </template>
            </el-table-column>
          </el-table>
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
              filterable @change="onSupplierChange">
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
                <el-select v-model="item.materialCode"
                  :placeholder="inboundForm.supplierCode ? '搜索物料号' : '请先选择供应商'"
                  size="small" filterable remote
                  :remote-method="(q) => searchMaterials(q, idx)"
                  :loading="materialSearchLoading[idx]" clearable style="width: 100%"
                  :disabled="!inboundForm.supplierCode"
                  @focus="searchMaterials('', idx)"
                  @change="(val) => fetchPackCapacity(idx, val)">
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
        <!-- 箱单标签展示（点击可下载完整标签 PNG） -->
        <div v-if="detailData && detailData.barcodes && detailData.barcodes.length > 0"
          class="barcode-gallery">
          <div class="barcode-gallery-title">箱单标签（共 {{ detailData.barcodes.length }} 个，点击可下载完整标签）</div>
          <div class="label-grid">
            <div v-for="bc in detailData.barcodes" :key="bc.barcode" class="label-card"
              @click="downloadLabel(bc, $event)">
              <div class="label-card-header">
                <span class="barcode-status-tag" :class="bc.status === '在库' ? 'tag-in-stock' : 'tag-pending'">
                  {{ bc.status }}
                </span>
                <el-icon :size="14" class="download-icon"><Download /></el-icon>
              </div>
              <BoxLabel :ref="el => setLabelRef(bc.barcode, el)"
                :barcode="bc.barcode"
                :status="bc.status"
                :order-no="detailData.orderNo"
                :created-at="bc.createdAt" />
            </div>
          </div>
        </div>
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
            <el-select v-model="editForm.supplierCode" placeholder="请选择供应商" style="width: 100%"
              filterable @change="onEditSupplierChange">
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
                <el-select v-model="item.materialCode"
                  :placeholder="editForm.supplierCode ? '搜索物料号' : '请先选择供应商'"
                  size="small" filterable remote
                  :remote-method="(q) => searchEditMaterials(q, idx)"
                  :loading="editMaterialLoading[idx]" clearable style="width: 100%"
                  :disabled="!editForm.supplierCode"
                  @focus="searchEditMaterials('', idx)"
                  @change="(val) => fetchEditPackCapacity(idx, val)">
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
            <!-- 箱单标签 -->
            <div v-if="printOrder.barcodes && printOrder.barcodes.length > 0"
              class="print-labels">
              <div class="barcode-gallery-title">箱单标签（共 {{ printOrder.barcodes.length }} 个）</div>
              <div class="label-print-grid">
                <div v-for="bc in printOrder.barcodes" :key="bc.barcode" class="label-print-item">
                  <BoxLabel :barcode="bc.barcode"
                    :status="bc.status"
                    :order-no="printOrder.orderNo"
                    :created-at="bc.createdAt" />
                </div>
              </div>
            </div>
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

    <!-- 新建出库单对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="outDialogVisible" title="新建出库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close class="inbound-dialog">
        <el-form ref="outFormRef" :model="outboundForm" label-width="88px">
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span>
                <span>单箱容量</span>
                <span>计划出库数</span>
                <span>操作</span>
              </div>
              <div v-for="(item, idx) in outboundForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                  filterable remote :remote-method="(q) => searchOutMaterials(q, idx)"
                  :loading="outMaterialLoading[idx]" clearable style="width: 100%"
                  @focus="searchOutMaterials('', idx)">
                  <el-option v-for="m in outMaterialOptions[idx]" :key="m.materialCode"
                    :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small"
                  controls-position="right" />
                <el-button type="danger" link size="small" @click="removeOutDetail(idx)"
                  :disabled="outboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>
                  <span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addOutDetail">
                  <el-icon :size="14"><Plus /></el-icon>
                  <span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">保存后可在列表中执行确认出库。</span>
            <div>
              <el-button @click="outDialogVisible = false">取消</el-button>
              <el-button type="primary" @click="handleOutCreate">保存</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 确认出库对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="outConfirmVisible" title="确认出库"
        width="min(800px, calc(100vw - 32px))" destroy-on-close>
        <el-alert title="系统将自动按先进先出 (FIFO) 规则校验条码，请确保按入库先后顺序选择条码。"
          type="info" show-icon :closable="false" style="margin-bottom: 12px" />
        <div class="confirm-info">
          <span class="confirm-label">出库单号：</span>
          <strong>{{ outConfirmTarget?.orderNo }}</strong>
          <span class="confirm-divider">|</span>
          <span class="confirm-label">状态：</span>
          <span class="badge" :class="outStatusClass(outConfirmTarget?.status)">
            {{ outConfirmTarget?.status }}
          </span>
        </div>
        <el-table :data="outConfirmDetails" stripe size="small" style="margin-top: 12px">
          <el-table-column prop="materialCode" label="物料号" width="130" />
          <el-table-column prop="planQty" label="计划数" width="80" align="right" />
          <el-table-column label="已出数" width="80" align="right">
            <template #default="{ row }">{{ row.actualQty || 0 }}</template>
          </el-table-column>
          <el-table-column label="本次出库数" width="130">
            <template #default="{ row, $index }">
              <el-input-number v-model="row._confirmQty" :min="0"
                :max="(row.planQty || 0) - (row.actualQty || 0)"
                size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="出库条码" min-width="220">
            <template #default="{ row }">
              <div class="barcode-tag-area">
                <el-tag v-for="(bc, i) in (row._barcodes || [])" :key="i"
                  size="small" closable class="barcode-tag"
                  @close="removeOutBarcode(row, i)">
                  {{ bc }}
                </el-tag>
                <el-input v-model="row._barcodeInput" placeholder="扫描/输入条码后回车"
                  size="small" class="barcode-input-inline"
                  @keyup.enter="addOutBarcode(row)"
                  @blur="addOutBarcode(row)" />
              </div>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">请逐行扫描或输入出库条码，系统将校验 FIFO 规则。</span>
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
        width="min(700px, calc(100vw - 32px))" destroy-on-close>
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
          <el-table-column prop="materialCode" label="物料号" width="140" />
          <el-table-column prop="packCapacity" label="单箱容量" width="90" align="right" />
          <el-table-column prop="planQty" label="计划数" width="80" align="right" />
          <el-table-column prop="actualQty" label="实出数" width="80" align="right" />
        </el-table>
        <!-- 出库标签画廊（出库单创建时已封装，可下载打印后用于扫码出库） -->
        <div v-if="outDetailData && outDetailData.barcodes && outDetailData.barcodes.length > 0"
          class="barcode-gallery">
          <div class="barcode-gallery-title">出库标签（共 {{ outDetailData.barcodes.length }} 个，点击可下载完整标签）</div>
          <div class="label-grid">
            <div v-for="bc in outDetailData.barcodes" :key="bc.barcode" class="label-card"
              @click="downloadOutLabel(bc, $event)">
              <div class="label-card-header">
                <span class="barcode-status-tag" :class="bc.status === '已出库' ? 'tag-outbound' : 'tag-pending'">
                  {{ bc.status }}
                </span>
                <el-icon :size="14" class="download-icon"><Download /></el-icon>
              </div>
              <BoxLabel :ref="el => setOutLabelRef(bc.barcode, el)"
                type="outbound"
                :barcode="bc.barcode"
                :status="bc.status"
                :order-no="outDetailData.orderNo"
                :created-at="bc.createdAt" />
            </div>
          </div>
        </div>
        <!-- 出库流水（确认后显示） -->
        <div v-if="outDetailData && outDetailData.histories && outDetailData.histories.length > 0"
          style="margin-top: 16px">
          <div class="barcode-gallery-title">出库流水（共 {{ outDetailData.histories.length }} 条）</div>
          <el-table :data="outDetailData.histories" stripe size="small">
            <el-table-column prop="barcode" label="条码号" min-width="260" show-overflow-tooltip />
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

    <!-- 修改出库单对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="outEditVisible" title="修改出库单"
        width="min(720px, calc(100vw - 32px))" destroy-on-close>
        <el-alert title="修改后将重新执行拆零拣选，原出库标签将被替换。"
          type="warning" show-icon :closable="false" class="draft-alert" />
        <el-form ref="outEditFormRef" :model="outEditForm" label-width="88px">
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="detail-head">
                <span>物料号</span><span>单箱容量</span><span>计划出库数</span><span>操作</span>
              </div>
              <div v-for="(item, idx) in outEditForm.details" :key="idx" class="detail-row">
                <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                  filterable remote :remote-method="(q) => searchOutEditMaterials(q, idx)"
                  :loading="outEditMaterialLoading[idx]" clearable style="width: 100%"
                  @focus="searchOutEditMaterials('', idx)">
                  <el-option v-for="m in outEditMaterialOptions[idx]" :key="m.materialCode"
                    :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                </el-select>
                <el-input-number v-model="item.packCapacity" :min="1" :max="999999" size="small" controls-position="right" />
                <el-input-number v-model="item.planQty" :min="1" :max="999999" size="small" controls-position="right" />
                <el-button type="danger" link size="small" @click="removeOutEditDetail(idx)"
                  :disabled="outEditForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon><span>删除</span>
                </el-button>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addOutEditDetail">
                  <el-icon :size="14"><Plus /></el-icon><span>添加物料行</span>
                </el-button>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">修改后需重新确认出库。</span>
            <div>
              <el-button @click="outEditVisible = false">取消</el-button>
              <el-button type="primary" :loading="outEditSubmitting" @click="handleOutEditSubmit">保存修改</el-button>
            </div>
          </div>
        </template>
      </el-dialog>
    </Teleport>

    <!-- 出库流水查询对话框 (Teleport to body) -->
    <Teleport to="body">
      <el-dialog v-model="historyVisible" title="出库批次流水查询"
        width="min(900px, calc(100vw - 32px))" destroy-on-close>
        <div class="toolbar" style="margin-bottom: 12px">
          <el-input v-model="historyQuery.orderNo" placeholder="出库单号（模糊）" clearable
            size="small" style="width: 180px" @keyup.enter="loadHistories" />
          <el-input v-model="historyQuery.materialCode" placeholder="物料号" clearable
            size="small" style="width: 140px" @keyup.enter="loadHistories" />
          <el-button type="primary" size="small" @click="loadHistories">查询</el-button>
        </div>
        <el-table :data="historyList" stripe size="small" v-loading="historyLoading"
          empty-text="暂无出库流水记录">
          <el-table-column prop="outboundOrderNo" label="出库单号" width="200" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料号" width="130" />
          <el-table-column prop="inboundOrderNo" label="来源入库单号" width="200" show-overflow-tooltip />
          <el-table-column prop="barcode" label="条码号" min-width="260" show-overflow-tooltip />
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
import { getInboundOrders, createInbound, updateInbound, confirmInbound, getInboundDetail } from '@/api/inbound'
import { getOutboundOrders, createOutbound, updateOutbound, deleteOutbound, confirmOutbound, getOutboundDetail, getOutboundHistories } from '@/api/outbound'
import { getSuppliers } from '@/api/suppliers'
import { getMaterials } from '@/api/materials'
import { getAppliances } from '@/api/appliances'
import { useUserStore } from '@/stores/user'
import QRCode from '@/components/QRCode.vue'
import BoxLabel from '@/components/BoxLabel.vue'

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

// ==================== 物料搜索 ====================
/**
 * 搜索物料（新建入库单），必须已选择供应商。
 */
async function searchMaterials(query, idx) {
  // 未选供应商时不发起搜索
  if (!inboundForm.supplierCode) {
    materialOptions.value[idx] = []
    return
  }
  materialSearchLoading.value[idx] = true
  try {
    const data = await getMaterials({
      page: 1, size: 20,
      keyword: query || undefined,
      supplierCode: inboundForm.supplierCode
    })
    materialOptions.value[idx] = data.records || []
  } catch {
    materialOptions.value[idx] = []
  } finally {
    materialSearchLoading.value[idx] = false
  }
}

/**
 * 当物料被选中时，自动从器具配置获取包装容量。
 */
async function fetchPackCapacity(idx, materialCode) {
  if (!materialCode || !inboundForm.supplierCode) return
  try {
    const data = await getAppliances({ page: 1, size: 10, keyword: materialCode })
    const match = (data.records || []).find(
      a => a.materialCode === materialCode && a.supplierCode === inboundForm.supplierCode
    )
    if (match && match.packCapacity > 0) {
      inboundForm.details[idx].packCapacity = match.packCapacity
    }
  } catch { /* 获取失败时保留默认容量 */ }
}

// ==================== 新建入库单 ====================
function addDetail() {
  inboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}
function removeDetail(idx) {
  if (inboundForm.details.length > 1) inboundForm.details.splice(idx, 1)
}

/**
 * 供应商切换时清空所有物料行。
 */
function onSupplierChange() {
  // 重置每行的物料选择
  inboundForm.details.forEach(item => { item.materialCode = '' })
  materialOptions.value = {}
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
  // 手动校验必填项，避免 ref 失效导致静默失败
  if (!inboundForm.supplierCode) {
    ElMessage.warning('请选择供应商')
    return
  }
  const invalidDetail = inboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.packCapacity || !item.planQty
  )
  if (invalidDetail) {
    ElMessage.warning('请完整填写每一行物料明细（物料号、单箱容量、计划入库数）')
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
    ElMessage.success('入库确认成功')
    confirmVisible.value = false
    loadOrders()
  } catch { /* */ } finally {
    confirmSubmitting.value = false
  }
}

// ==================== 修改入库单 ====================
/**
 * 搜索物料（编辑入库单），必须已选择供应商。
 */
async function searchEditMaterials(query, idx) {
  if (!editForm.supplierCode) {
    editMaterialOptions.value[idx] = []
    return
  }
  editMaterialLoading.value[idx] = true
  try {
    const data = await getMaterials({
      page: 1, size: 20,
      keyword: query || undefined,
      supplierCode: editForm.supplierCode
    })
    editMaterialOptions.value[idx] = data.records || []
  } catch {
    editMaterialOptions.value[idx] = []
  } finally {
    editMaterialLoading.value[idx] = false
  }
}

/**
 * 编辑对话框：当物料被选中时，自动从器具配置获取包装容量。
 */
async function fetchEditPackCapacity(idx, materialCode) {
  if (!materialCode || !editForm.supplierCode) return
  try {
    const data = await getAppliances({ page: 1, size: 10, keyword: materialCode })
    const match = (data.records || []).find(
      a => a.materialCode === materialCode && a.supplierCode === editForm.supplierCode
    )
    if (match && match.packCapacity > 0) {
      editForm.details[idx].packCapacity = match.packCapacity
    }
  } catch { /* 获取失败时保留默认容量 */ }
}

/**
 * 编辑对话框：供应商切换时清空所有物料行。
 */
function onEditSupplierChange() {
  editForm.details.forEach(item => { item.materialCode = '' })
  editMaterialOptions.value = {}
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
  // 手动校验必填项，避免 ref 失效导致静默失败
  if (!editForm.supplierCode) {
    ElMessage.warning('请选择供应商')
    return
  }
  const invalid = editForm.details.find(d =>
    !d.materialCode?.trim() || !d.packCapacity || !d.planQty
  )
  if (invalid) {
    ElMessage.warning('请完整填写每一行物料明细（物料号、单箱容量、计划入库数）')
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
 * 从条码字符串中提取用于文件名的物料编码和箱号。
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

  // 从条码字符串解析箱号: OUT|MAT|CK20260615...|15|90|15|3 → boxSeq=7
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
      .print-labels { margin-top: 16px; padding-top: 12px; border-top: 1px solid #ccc; }
      .barcode-gallery-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
      .label-print-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
      .label-print-item { display: flex; justify-content: center; page-break-inside: avoid; }
      .label-print-item canvas { max-width: 100%; height: auto; }
      .box-label-canvas { box-shadow: none !important; }
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

// ==================== 出库管理 ====================
const outboundList = ref([])
const outboundLoading = ref(false)

// 统计
const outPendingCount = computed(() => outboundList.value.filter(r => r.status !== '已完成').length)
const outCompletedCount = computed(() => outboundList.value.filter(r => r.status === '已完成').length)

function outStatusClass(status) {
  if (status === '已完成') return 'badge-success'
  if (status === '部分出库') return 'badge-warn'
  return 'badge-default'
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
const outMaterialOptions = ref({})
const outMaterialLoading = ref({})

const outboundForm = reactive({
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})

function addOutDetail() {
  outboundForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 })
}
function removeOutDetail(idx) {
  if (outboundForm.details.length > 1) outboundForm.details.splice(idx, 1)
}

function openOutboundDialog() {
  outboundForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
  outMaterialOptions.value = {}
  outDialogVisible.value = true
}

async function searchOutMaterials(query, idx) {
  outMaterialLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    outMaterialOptions.value[idx] = data.records || []
  } catch {
    outMaterialOptions.value[idx] = []
  } finally {
    outMaterialLoading.value[idx] = false
  }
}

async function handleOutCreate() {
  const invalid = outboundForm.details.find(item =>
    !item.materialCode?.trim() || !item.packCapacity || !item.planQty
  )
  if (invalid) {
    ElMessage.warning('请完整填写每一行物料明细')
    return
  }
  try {
    await createOutbound({ details: outboundForm.details })
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
const outEditMaterialOptions = ref({})
const outEditMaterialLoading = ref({})
const outEditForm = reactive({
  details: [{ materialCode: '', packCapacity: 20, planQty: 200 }]
})

function addOutEditDetail() { outEditForm.details.push({ materialCode: '', packCapacity: 20, planQty: 100 }) }
function removeOutEditDetail(idx) { if (outEditForm.details.length > 1) outEditForm.details.splice(idx, 1) }

async function searchOutEditMaterials(query, idx) {
  outEditMaterialLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    outEditMaterialOptions.value[idx] = data.records || []
  } catch { outEditMaterialOptions.value[idx] = [] }
  finally { outEditMaterialLoading.value[idx] = false }
}

async function openOutEditDialog(row) {
  outEditTarget.value = row
  outEditSubmitting.value = false
  outEditMaterialOptions.value = {}
  try {
    const data = await getOutboundDetail(row.id)
    outEditForm.details = (data.details || []).map(d => ({
      materialCode: d.materialCode || '',
      packCapacity: d.packCapacity || 20,
      planQty: d.planQty || 100
    }))
    if (outEditForm.details.length === 0) {
      outEditForm.details = [{ materialCode: '', packCapacity: 20, planQty: 200 }]
    }
    outEditVisible.value = true
  } catch { ElMessage.error('加载出库单详情失败') }
}

async function handleOutEditSubmit() {
  const invalid = outEditForm.details.find(d =>
    !d.materialCode?.trim() || !d.packCapacity || !d.planQty
  )
  if (invalid) { ElMessage.warning('请完整填写每一行物料明细'); return }
  outEditSubmitting.value = true
  try {
    await updateOutbound(outEditTarget.value.id, { details: outEditForm.details })
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
    // 为每行添加 _confirmQty 和 _barcodes / _barcodeInput 字段
    outConfirmDetails.value = (data.details || []).map(d => ({
      ...d,
      _confirmQty: Math.max(0, (d.planQty || 0) - (d.actualQty || 0)),
      _barcodes: [],
      _barcodeInput: ''
    }))
  } catch {
    outConfirmDetails.value = []
  }
  outConfirmVisible.value = true
}

/**
 * 将条码输入框中的值添加为条码标签。
 * 支持扫码枪回车输入和手动粘贴（按逗号/空格/换行分割）。
 */
function addOutBarcode(row) {
  const raw = (row._barcodeInput || '').trim()
  if (!raw) return
  // 支持逗号、空格、换行作为分隔符，适配批量粘贴
  const parts = raw.split(/[,，\s\n\r]+/).filter(Boolean)
  if (!row._barcodes) row._barcodes = []
  for (const part of parts) {
    if (!row._barcodes.includes(part)) {
      row._barcodes.push(part)
    }
  }
  row._barcodeInput = ''
}

function removeOutBarcode(row, idx) {
  row._barcodes.splice(idx, 1)
}

async function handleOutConfirmSubmit() {
  // 校验：每条明细的条码折算数量应与本次出库数一致
  for (const row of outConfirmDetails.value) {
    if (row._confirmQty > 0 && (!row._barcodes || row._barcodes.length === 0)) {
      ElMessage.warning(`物料 ${row.materialCode} 本次出库数大于 0，请扫描或输入条码`)
      return
    }
  }

  outConfirmSubmitting.value = true
  try {
    const details = outConfirmDetails.value
      .filter(d => d._confirmQty > 0)
      .map(d => ({
        detailId: d.id,
        actualQty: d._confirmQty,
        barcodes: d._barcodes || []
      }))
    if (details.length === 0) {
      ElMessage.warning('请至少填写一条明细的本次出库数')
      outConfirmSubmitting.value = false
      return
    }
    await confirmOutbound(outConfirmTarget.value.id, { details })
    ElMessage.success('出库确认成功')
    outConfirmVisible.value = false
    loadOutboundOrders()
  } catch (err) {
    ElMessage.error(err.message || '出库确认失败，请检查条码是否正确且符合先进先出规则')
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

// ==================== 出库流水查询 ====================
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])
const historyQuery = reactive({ orderNo: '', materialCode: '' })

function openHistoryDialog() {
  historyQuery.orderNo = ''
  historyQuery.materialCode = ''
  historyList.value = []
  historyVisible.value = true
}

async function loadHistories() {
  historyLoading.value = true
  try {
    const params = { page: 1, size: 50 }
    if (historyQuery.orderNo.trim()) params.orderNo = historyQuery.orderNo.trim()
    if (historyQuery.materialCode.trim()) params.materialCode = historyQuery.materialCode.trim()
    const data = await getOutboundHistories(params)
    historyList.value = data.records || []
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
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
/* 使用网格布局适配长方形标签 */
.label-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
  max-height: 560px;
  overflow-y: auto;
}
.label-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 10px 6px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.label-card:hover {
  border-color: var(--wms-primary);
  box-shadow: 0 1px 8px rgba(64, 158, 255, 0.18);
}
.label-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 2px;
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

/* ==================== 打印预览 — 标签网格 ==================== */
.print-labels {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
.label-print-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.label-print-item {
  display: flex;
  justify-content: center;
}
.label-print-item :deep(.box-label-canvas) {
  box-shadow: none;
  border: 1px solid #ccc;
}
@media (max-width: 520px) {
  .label-print-grid {
    grid-template-columns: 1fr;
  }
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

/* ==================== 出库条码输入区 ==================== */
.barcode-tag-area {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 32px;
  padding: 4px 6px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: #fafafa;
}
.barcode-tag-area:focus-within {
  border-color: var(--wms-primary);
  background: #fff;
}
.barcode-tag {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.barcode-input-inline {
  flex: 1;
  min-width: 120px;
}
.barcode-input-inline :deep(.el-input__wrapper) {
  border: none;
  box-shadow: none;
  background: transparent;
  padding: 0 4px;
}
.barcode-input-inline :deep(.el-input__wrapper:hover),
.barcode-input-inline :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
}
</style>
