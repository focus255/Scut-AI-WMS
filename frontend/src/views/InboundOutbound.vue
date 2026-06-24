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
            <el-table-column label="操作" width="280" align="center">
              <template #default="{ row }">
                <el-button v-if="row.status !== '已完成'" type="primary" link size="small"
                  @click.stop="openOutEditDialog(row)">
                  编辑
                </el-button>
                <el-button v-if="row.status === '未完成'" type="danger" link size="small"
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

    <!-- 新建入库单右侧抽屉 -->
    <el-drawer v-model="dialogVisible" title="新建入库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
      @opened="onCreateDialogOpened">
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

          <!-- 物料目录（批量选择 — 聚合所有选中供应商的物料） -->
          <el-form-item v-if="inboundForm.selectedSuppliers.length > 0" label="物料目录">
            <div class="catalog-panel">
              <div class="catalog-toolbar">
                <el-input v-model="catalogSearch" placeholder="搜索物料号或名称" size="small"
                  clearable style="width: 200px" />
                <el-button size="small" @click="selectAllCatalog">全选</el-button>
                <el-button size="small" @click="invertCatalog">反选</el-button>
                <span class="catalog-hint">已选 {{ catalogChecked.length }} / {{ catalogMaterials.length }} 种</span>
              </div>
              <div class="catalog-scroll">
                <el-table :data="filteredCatalog" size="small" max-height="300"
                  @selection-change="onCatalogSelect" ref="catalogTableRef" row-key="materialCode">
                  <el-table-column type="selection" width="36" :selectable="catalogSelectable" />
                  <el-table-column prop="materialCode" label="物料号" width="130" />
                  <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
                  <el-table-column label="供应商" width="120" show-overflow-tooltip>
                    <template #default="{ row }">
                      <span class="supplier-code-text">{{ row._supplierCode || '' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="器具类型" width="100">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packType || '未配置' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单箱容量" width="80" align="right">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packCapacity || '—' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="catalog-actions">
                <el-button type="primary" size="small" @click="batchAddToInbound"
                  :disabled="catalogChecked.length === 0">
                  <el-icon :size="14"><Plus /></el-icon>
                  批量添加选中物料 ({{ catalogChecked.length }})
                </el-button>
              </div>
            </div>
          </el-form-item>

          <!-- 物料明细编辑区 -->
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <!-- 批量操作工具栏 -->
              <div class="batch-toolbar">
                <span class="batch-label">统一箱数</span>
                <el-input-number v-model="uniformBoxCount" :min="1" :max="999999" size="small"
                  controls-position="right" style="width: 120px" />
                <el-button size="small" @click="applyUniformBoxCount('inbound')">应用</el-button>
                <el-divider direction="vertical" />
                <el-button size="small" @click="clearAllDetails('inbound')" :disabled="inboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>清空列表
                </el-button>
                <span class="batch-summary">
                  共 <b>{{ inboundForm.details.filter(d => d.materialCode).length }}</b> 种物料 /
                  <b>{{ inboundTotalBoxes }}</b> 箱 /
                  <b>{{ inboundTotalQty }}</b> 件
                </span>
              </div>
              <!-- 表头 -->
              <div class="detail-head detail-head-7">
                <span class="col-seq">#</span>
                <span>物料号</span>
                <span>器具类型</span>
                <span>单箱容量</span>
                <span>入库箱数</span>
                <span>总数</span>
                <span>操作</span>
              </div>
              <!-- 明细行滚动区 -->
              <div class="detail-scroll">
                <div v-for="(item, idx) in inboundForm.details" :key="idx" class="detail-row detail-row-7">
                  <span class="col-seq seq-num">{{ idx + 1 }}</span>
                  <el-select v-model="item.materialCode"
                    :placeholder="inboundForm.selectedSuppliers.length > 0 ? '搜索物料号' : '请先添加供应商'"
                    size="small" filterable remote
                    :remote-method="(q) => searchMaterials(q, idx)"
                    :loading="materialSearchLoading[idx]" clearable style="width: 100%"
                    :disabled="inboundForm.selectedSuppliers.length === 0"
                    @focus="searchMaterials('', idx)"
                    @change="(val) => fetchPackCapacity(idx, val)">
                    <el-option v-for="m in materialOptions[idx]" :key="m.materialCode"
                      :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                  </el-select>
                  <span class="readonly-cell" :title="item.packType">{{ item.packType || '—' }}</span>
                  <span class="readonly-cell">{{ item.packCapacity || '—' }}</span>
                  <el-input-number v-model="item.boxCount" :min="1" :max="999999" size="small"
                    controls-position="right" />
                  <span class="readonly-cell total-cell">{{ calcRowTotal(item) }}</span>
                  <el-button type="danger" link size="small" @click="removeDetail(idx)"
                    :disabled="inboundForm.details.length <= 1">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addDetail">
                  <el-icon :size="14"><Plus /></el-icon>手动添加一行
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
      @opened="onEditDialogOpened">
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

          <!-- 物料目录（批量选择） -->
          <el-form-item v-if="editForm.supplierCode" label="物料目录">
            <div class="catalog-panel">
              <div class="catalog-toolbar">
                <el-input v-model="editCatalogSearch" placeholder="搜索物料号或名称" size="small"
                  clearable style="width: 200px" />
                <el-button size="small" @click="selectAllEditCatalog">全选</el-button>
                <el-button size="small" @click="invertEditCatalog">反选</el-button>
                <span class="catalog-hint">已选 {{ editCatalogChecked.length }} / {{ editCatalogMaterials.length }} 种</span>
              </div>
              <div class="catalog-scroll">
                <el-table :data="filteredEditCatalog" size="small" max-height="300"
                  @selection-change="onEditCatalogSelect" ref="editCatalogTableRef" row-key="materialCode">
                  <el-table-column type="selection" width="36" :selectable="editCatalogSelectable" />
                  <el-table-column prop="materialCode" label="物料号" width="140" />
                  <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                  <el-table-column label="器具类型" width="110">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packType || '未配置' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单箱容量" width="80" align="right">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packCapacity || '—' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="catalog-actions">
                <el-button type="primary" size="small" @click="batchAddToEditInbound"
                  :disabled="editCatalogChecked.length === 0">
                  <el-icon :size="14"><Plus /></el-icon>
                  批量添加选中物料 ({{ editCatalogChecked.length }})
                </el-button>
              </div>
            </div>
          </el-form-item>

          <!-- 物料明细编辑区 -->
          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="batch-toolbar">
                <span class="batch-label">统一箱数</span>
                <el-input-number v-model="editUniformBoxCount" :min="1" :max="999999" size="small"
                  controls-position="right" style="width: 120px" />
                <el-button size="small" @click="applyUniformBoxCount('editInbound')">应用</el-button>
                <el-divider direction="vertical" />
                <el-button size="small" @click="clearAllDetails('editInbound')" :disabled="editForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>清空列表
                </el-button>
                <span class="batch-summary">
                  共 <b>{{ editForm.details.filter(d => d.materialCode).length }}</b> 种物料 /
                  <b>{{ editInboundTotalBoxes }}</b> 箱 /
                  <b>{{ editInboundTotalQty }}</b> 件
                </span>
              </div>
              <div class="detail-head detail-head-7">
                <span class="col-seq">#</span>
                <span>物料号</span><span>器具类型</span><span>单箱容量</span><span>入库箱数</span><span>总数</span><span>操作</span>
              </div>
              <div class="detail-scroll">
                <div v-for="(item, idx) in editForm.details" :key="idx" class="detail-row detail-row-7">
                  <span class="col-seq seq-num">{{ idx + 1 }}</span>
                  <el-select v-model="item.materialCode"
                    placeholder="搜索物料号"
                    size="small" filterable remote
                    :remote-method="(q) => searchEditMaterials(q, idx)"
                    :loading="editMaterialLoading[idx]" clearable style="width: 100%"
                    @focus="searchEditMaterials('', idx)"
                    @change="(val) => fetchEditPackCapacity(idx, val)">
                    <el-option v-for="m in editMaterialOptions[idx]" :key="m.materialCode"
                      :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                  </el-select>
                  <span class="readonly-cell" :title="item.packType">{{ item.packType || '—' }}</span>
                  <span class="readonly-cell">{{ item.packCapacity || '—' }}</span>
                  <el-input-number v-model="item.boxCount" :min="1" :max="999999" size="small" controls-position="right" />
                  <span class="readonly-cell total-cell">{{ calcRowTotal(item) }}</span>
                  <el-button type="danger" link size="small" @click="removeEditDetail(idx)"
                    :disabled="editForm.details.length <= 1">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addEditDetail">
                  <el-icon :size="14"><Plus /></el-icon>手动添加一行
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
            <!-- 入库看板 -->
            <div v-if="printOrder.barcodes && printOrder.barcodes.length > 0"
              class="print-labels">
              <div class="barcode-gallery-title">入库看板（共 {{ printOrder.barcodes.length }} 个）</div>
              <div class="label-print-grid">
                <div v-for="bc in printOrder.barcodes" :key="bc.barcode" class="label-print-item">
                  <BoxLabel :barcode="bc.barcode"
                    :created-at="bc.createdAt" />
                  <div class="label-stamp" :class="'stamp-' + stampClass(bc.status)">{{ bc.status || '在库' }}</div>
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

    <!-- 新建出库单右侧抽屉 -->
    <el-drawer v-model="outDialogVisible" title="新建出库单"
      direction="rtl" size="65%" destroy-on-close :close-on-click-modal="false"
      @opened="onOutDialogOpened">
        <el-form ref="outFormRef" :model="outboundForm" label-width="88px">

          <!-- 物料目录（全部物料） -->
          <el-form-item label="物料目录">
            <div class="catalog-panel">
              <div class="catalog-toolbar">
                <el-input v-model="outCatalogSearch" placeholder="搜索物料号或名称" size="small"
                  clearable style="width: 200px" />
                <el-button size="small" @click="selectAllOutCatalog">全选</el-button>
                <el-button size="small" @click="invertOutCatalog">反选</el-button>
                <span class="catalog-hint">已选 {{ outCatalogChecked.length }} / {{ outCatalogMaterials.length }} 种</span>
              </div>
              <div class="catalog-scroll">
                <el-table :data="filteredOutCatalog" size="small" max-height="300"
                  @selection-change="onOutCatalogSelect" ref="outCatalogTableRef" row-key="materialCode">
                  <el-table-column type="selection" width="36" :selectable="outCatalogSelectable" />
                  <el-table-column prop="materialCode" label="物料号" width="140" />
                  <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                  <el-table-column label="器具类型" width="110">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packType || '未配置' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单箱容量" width="80" align="right">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packCapacity || '—' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="catalog-actions">
                <el-button type="primary" size="small" @click="batchAddToOutbound"
                  :disabled="outCatalogChecked.length === 0">
                  <el-icon :size="14"><Plus /></el-icon>
                  批量添加选中物料 ({{ outCatalogChecked.length }})
                </el-button>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="batch-toolbar">
                <span class="batch-label">统一箱数</span>
                <el-input-number v-model="outUniformBoxCount" :min="1" :max="999999" size="small"
                  controls-position="right" style="width: 120px" />
                <el-button size="small" @click="applyUniformBoxCount('outbound')">应用</el-button>
                <el-divider direction="vertical" />
                <el-button size="small" @click="clearAllDetails('outbound')" :disabled="outboundForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>清空列表
                </el-button>
                <span class="batch-summary">
                  共 <b>{{ outboundForm.details.filter(d => d.materialCode).length }}</b> 种物料 /
                  <b>{{ outTotalBoxes }}</b> 箱 /
                  <b>{{ outTotalQty }}</b> 件
                </span>
              </div>
              <div class="detail-head detail-head-7">
                <span class="col-seq">#</span>
                <span>物料号</span>
                <span>器具类型</span>
                <span>单箱容量</span>
                <span>出库箱数</span>
                <span>总数</span>
                <span>操作</span>
              </div>
              <div class="detail-scroll">
                <div v-for="(item, idx) in outboundForm.details" :key="idx" class="detail-row detail-row-7">
                  <span class="col-seq seq-num">{{ idx + 1 }}</span>
                  <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                    filterable remote :remote-method="(q) => searchOutMaterials(q, idx)"
                    :loading="outMaterialLoading[idx]" clearable style="width: 100%"
                    @focus="searchOutMaterials('', idx)"
                    @change="(val) => fetchOutPackCapacity(idx, val)">
                    <el-option v-for="m in outMaterialOptions[idx]" :key="m.materialCode"
                      :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                  </el-select>
                  <span class="readonly-cell" :title="item.packType">{{ item.packType || '—' }}</span>
                  <span class="readonly-cell">{{ item.packCapacity || '—' }}</span>
                  <el-input-number v-model="item.boxCount" :min="1" :max="999999" size="small"
                    controls-position="right" />
                  <span class="readonly-cell total-cell">{{ calcRowTotal(item) }}</span>
                  <el-button type="danger" link size="small" @click="removeOutDetail(idx)"
                    :disabled="outboundForm.details.length <= 1">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addOutDetail">
                  <el-icon :size="14"><Plus /></el-icon>手动添加一行
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
      <el-dialog v-model="outConfirmVisible" title="确认出库"
        width="min(800px, calc(100vw - 32px))" destroy-on-close>
        <el-alert title="系统将自动按先进先出 (FIFO) 规则校验二维码，请确保按入库先后顺序选择二维码。"
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
          <el-table-column prop="materialCode" label="物料号" min-width="130" />
          <el-table-column prop="planQty" label="计划数" width="90" align="center" />
          <el-table-column label="已出数" width="90" align="center">
            <template #default="{ row }">{{ row.actualQty || 0 }}</template>
          </el-table-column>
          <el-table-column label="本次出库数" width="130" align="center">
            <template #default="{ row, $index }">
              <el-input-number v-model="row._confirmQty" :min="0"
                :max="(row.planQty || 0) - (row.actualQty || 0)"
                size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="出库二维码" min-width="220">
            <template #default="{ row }">
              <div class="barcode-tag-area">
                <el-tag v-for="(bc, i) in (row._barcodes || [])" :key="i"
                  size="small" closable class="barcode-tag"
                  @close="removeOutBarcode(row, i)">
                  {{ bc }}
                </el-tag>
                <el-input v-model="row._barcodeInput" placeholder="扫描/输入二维码后回车"
                  size="small" class="barcode-input-inline"
                  @keyup.enter="addOutBarcode(row)"
                  @blur="addOutBarcode(row)" />
              </div>
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <div class="dialog-footer">
            <span class="footer-tip">请逐行扫描或输入出库二维码，系统将校验 FIFO 规则。</span>
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
      @opened="onOutEditDialogOpened">
        <el-alert title="修改后将重新执行整箱拣选，原出库看板标签将被替换。"
          type="warning" show-icon :closable="false" class="draft-alert" />
        <el-form ref="outEditFormRef" :model="outEditForm" label-width="88px">

          <!-- 物料目录（全部物料） -->
          <el-form-item label="物料目录">
            <div class="catalog-panel">
              <div class="catalog-toolbar">
                <el-input v-model="outEditCatalogSearch" placeholder="搜索物料号或名称" size="small"
                  clearable style="width: 200px" />
                <el-button size="small" @click="selectAllOutEditCatalog">全选</el-button>
                <el-button size="small" @click="invertOutEditCatalog">反选</el-button>
                <span class="catalog-hint">已选 {{ outEditCatalogChecked.length }} / {{ outEditCatalogMaterials.length }} 种</span>
              </div>
              <div class="catalog-scroll">
                <el-table :data="filteredOutEditCatalog" size="small" max-height="300"
                  @selection-change="onOutEditCatalogSelect" ref="outEditCatalogTableRef" row-key="materialCode">
                  <el-table-column type="selection" width="36" :selectable="outEditCatalogSelectable" />
                  <el-table-column prop="materialCode" label="物料号" width="140" />
                  <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                  <el-table-column label="器具类型" width="110">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packType || '未配置' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="单箱容量" width="80" align="right">
                    <template #default="{ row }">
                      <span :class="{ 'text-warn': !row._appliance }">{{ row._packCapacity || '—' }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="catalog-actions">
                <el-button type="primary" size="small" @click="batchAddToOutEdit"
                  :disabled="outEditCatalogChecked.length === 0">
                  <el-icon :size="14"><Plus /></el-icon>
                  批量添加选中物料 ({{ outEditCatalogChecked.length }})
                </el-button>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="物料明细" prop="details">
            <div class="detail-editor">
              <div class="batch-toolbar">
                <span class="batch-label">统一箱数</span>
                <el-input-number v-model="outEditUniformBoxCount" :min="1" :max="999999" size="small"
                  controls-position="right" style="width: 120px" />
                <el-button size="small" @click="applyUniformBoxCount('outEdit')">应用</el-button>
                <el-divider direction="vertical" />
                <el-button size="small" @click="clearAllDetails('outEdit')" :disabled="outEditForm.details.length <= 1">
                  <el-icon :size="14"><Delete /></el-icon>清空列表
                </el-button>
                <span class="batch-summary">
                  共 <b>{{ outEditForm.details.filter(d => d.materialCode).length }}</b> 种物料 /
                  <b>{{ outEditTotalBoxes }}</b> 箱 /
                  <b>{{ outEditTotalQty }}</b> 件
                </span>
              </div>
              <div class="detail-head detail-head-7">
                <span class="col-seq">#</span>
                <span>物料号</span><span>器具类型</span><span>单箱容量</span><span>出库箱数</span><span>总数</span><span>操作</span>
              </div>
              <div class="detail-scroll">
                <div v-for="(item, idx) in outEditForm.details" :key="idx" class="detail-row detail-row-7">
                  <span class="col-seq seq-num">{{ idx + 1 }}</span>
                  <el-select v-model="item.materialCode" placeholder="搜索物料号" size="small"
                    filterable remote :remote-method="(q) => searchOutEditMaterials(q, idx)"
                    :loading="outEditMaterialLoading[idx]" clearable style="width: 100%"
                    @focus="searchOutEditMaterials('', idx)"
                    @change="(val) => fetchOutEditPackCapacity(idx, val)">
                    <el-option v-for="m in outEditMaterialOptions[idx]" :key="m.materialCode"
                      :label="`${m.materialCode} — ${m.materialName}`" :value="m.materialCode" />
                  </el-select>
                  <span class="readonly-cell" :title="item.packType">{{ item.packType || '—' }}</span>
                  <span class="readonly-cell">{{ item.packCapacity || '—' }}</span>
                  <el-input-number v-model="item.boxCount" :min="1" :max="999999" size="small" controls-position="right" />
                  <span class="readonly-cell total-cell">{{ calcRowTotal(item) }}</span>
                  <el-button type="danger" link size="small" @click="removeOutEditDetail(idx)"
                    :disabled="outEditForm.details.length <= 1">
                    <el-icon :size="14"><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <div class="detail-actions">
                <el-button type="primary" link size="small" @click="addOutEditDetail">
                  <el-icon :size="14"><Plus /></el-icon>手动添加一行
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

    <!-- 入库流水查询对话框 -->
    <Teleport to="body">
      <el-dialog v-model="inboundFlowVisible" title="入库流水查询"
        width="min(900px, calc(100vw - 32px))" destroy-on-close>
        <div class="toolbar" style="margin-bottom: 12px">
          <el-input v-model="inboundFlowQuery.orderNo" placeholder="入库单号（模糊）" clearable
            size="small" style="width: 180px" @keyup.enter="loadInboundFlow" />
          <el-input v-model="inboundFlowQuery.materialCode" placeholder="物料号" clearable
            size="small" style="width: 140px" @keyup.enter="loadInboundFlow" />
          <el-button type="primary" size="small" @click="loadInboundFlow">查询</el-button>
        </div>
        <el-table :data="inboundFlowList" stripe size="small" v-loading="inboundFlowLoading"
          empty-text="暂无入库流水记录">
          <el-table-column label="入库单号" width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ getInboundOrderNo(row.inboundId) }}
            </template>
          </el-table-column>
          <el-table-column prop="materialCode" label="物料号" width="130" />
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
        <template #footer>
          <el-button @click="inboundFlowVisible = false">关闭</el-button>
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
import { getInboundOrders, createInbound, updateInbound, confirmInbound, getInboundDetail, getInboundFlow } from '@/api/inbound'
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

// ==================== 物料目录（批量选择） ====================
const catalogMaterials = ref([])
const catalogChecked = ref([])
const catalogSearch = ref('')
const catalogTableRef = ref(null)
const uniformBoxCount = ref(1)

const editCatalogMaterials = ref([])
const editCatalogChecked = ref([])
const editCatalogSearch = ref('')
const editCatalogTableRef = ref(null)
const editUniformBoxCount = ref(1)

const outCatalogMaterials = ref([])
const outCatalogChecked = ref([])
const outCatalogSearch = ref('')
const outCatalogTableRef = ref(null)
const outUniformBoxCount = ref(1)

const outEditCatalogMaterials = ref([])
const outEditCatalogChecked = ref([])
const outEditCatalogSearch = ref('')
const outEditCatalogTableRef = ref(null)
const outEditUniformBoxCount = ref(1)

const pendingSuppliers = ref([]) // 待确认的多选供应商

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
  inboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) * (d.packCapacity || 0) : 0), 0)
)
const editInboundTotalBoxes = computed(() =>
  editForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const editInboundTotalQty = computed(() =>
  editForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) * (d.packCapacity || 0) : 0), 0)
)
const outTotalBoxes = computed(() =>
  outboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const outTotalQty = computed(() =>
  outboundForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) * (d.packCapacity || 0) : 0), 0)
)
const outEditTotalBoxes = computed(() =>
  outEditForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) : 0), 0)
)
const outEditTotalQty = computed(() =>
  outEditForm.details.reduce((sum, d) => sum + (d.materialCode ? (d.boxCount || 0) * (d.packCapacity || 0) : 0), 0)
)

const filteredCatalog = computed(() => {
  if (!catalogSearch.value) return catalogMaterials.value
  const kw = catalogSearch.value.toLowerCase()
  return catalogMaterials.value.filter(m => m.materialCode?.toLowerCase().includes(kw) || m.materialName?.toLowerCase().includes(kw))
})
const filteredEditCatalog = computed(() => {
  if (!editCatalogSearch.value) return editCatalogMaterials.value
  const kw = editCatalogSearch.value.toLowerCase()
  return editCatalogMaterials.value.filter(m => m.materialCode?.toLowerCase().includes(kw) || m.materialName?.toLowerCase().includes(kw))
})
const filteredOutCatalog = computed(() => {
  if (!outCatalogSearch.value) return outCatalogMaterials.value
  const kw = outCatalogSearch.value.toLowerCase()
  return outCatalogMaterials.value.filter(m => m.materialCode?.toLowerCase().includes(kw) || m.materialName?.toLowerCase().includes(kw))
})
const filteredOutEditCatalog = computed(() => {
  if (!outEditCatalogSearch.value) return outEditCatalogMaterials.value
  const kw = outEditCatalogSearch.value.toLowerCase()
  return outEditCatalogMaterials.value.filter(m => m.materialCode?.toLowerCase().includes(kw) || m.materialName?.toLowerCase().includes(kw))
})

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
  details: [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
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
  details: [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
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
  if (inboundForm.selectedSuppliers.length === 0) {
    materialOptions.value[idx] = []
    return
  }
  materialSearchLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    // 仅显示选中供应商的物料
    const supplierSet = new Set(inboundForm.selectedSuppliers)
    materialOptions.value[idx] = (data.records || []).filter(m => supplierSet.has(m.supplierCode))
  } catch {
    materialOptions.value[idx] = []
  } finally {
    materialSearchLoading.value[idx] = false
  }
}

/**
 * 当物料被选中时，自动从器具配置获取包装容量和器具类型（只读）。
 * 按物料号匹配，不限定供应商。
 */
async function fetchPackCapacity(idx, materialCode) {
  if (!materialCode) {
    inboundForm.details[idx].packType = ''
    inboundForm.details[idx].packCapacity = 0
    return
  }
  try {
    const data = await getAppliances({ page: 1, size: 20, keyword: materialCode })
    const match = (data.records || []).find(a => a.materialCode === materialCode)
    if (match && match.packCapacity > 0) {
      inboundForm.details[idx].packType = match.packType || ''
      inboundForm.details[idx].packCapacity = match.packCapacity
    } else {
      inboundForm.details[idx].packType = ''
      inboundForm.details[idx].packCapacity = 0
    }
  } catch { /* 获取失败时保留默认值 */ }
}

// ==================== 新建入库单 ====================
function addDetail() {
  inboundForm.details.push({ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 })
}
function removeDetail(idx) {
  if (inboundForm.details.length > 1) inboundForm.details.splice(idx, 1)
}

/** 获取供应商名称 */
function getSupplierName(code) {
  const s = supplierOptions.value.find(s => s.supplierCode === code)
  return s ? `${s.supplierName} (${s.supplierCode})` : code
}

/** 确认添加多选的供应商标签 → 合并加载物料目录 */
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
    materialOptions.value = {}
    loadMultiSupplierCatalog()
    ElMessage.success(`已添加 ${added} 家供应商`)
  }
}

/** 添加单个供应商（保留兼容） */
function addSupplier(code) {
  if (!inboundForm.selectedSuppliers.includes(code)) {
    inboundForm.selectedSuppliers.push(code)
    pendingSuppliers.value = []
    materialOptions.value = {}
    loadMultiSupplierCatalog()
  }
}

/** 移除供应商标签 → 刷新目录 */
function removeSupplier(code) {
  const idx = inboundForm.selectedSuppliers.indexOf(code)
  if (idx >= 0) inboundForm.selectedSuppliers.splice(idx, 1)
  materialOptions.value = {}
  loadMultiSupplierCatalog()
}

function openInboundDialog() {
  isAiDraft.value = false
  inboundForm.selectedSuppliers = []
  pendingSuppliers.value = []
  inboundForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
  catalogMaterials.value = []
  materialOptions.value = {}
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
    boxCount
  }]
  catalogMaterials.value = []
  materialOptions.value = {}
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
      details: inboundForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount }))
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
 * 编辑对话框：当物料被选中时，自动从器具配置获取包装容量和器具类型。
 */
async function fetchEditPackCapacity(idx, materialCode) {
  if (!materialCode || !editForm.supplierCode) {
    editForm.details[idx].packType = ''
    editForm.details[idx].packCapacity = 0
    return
  }
  try {
    const data = await getAppliances({ page: 1, size: 10, keyword: materialCode })
    const match = (data.records || []).find(
      a => a.materialCode === materialCode && a.supplierCode === editForm.supplierCode
    )
    if (match && match.packCapacity > 0) {
      editForm.details[idx].packType = match.packType || ''
      editForm.details[idx].packCapacity = match.packCapacity
    } else {
      editForm.details[idx].packType = ''
      editForm.details[idx].packCapacity = 0
    }
  } catch { /* 获取失败时保留默认值 */ }
}

/**
 * 编辑对话框：供应商切换时清空所有物料行并加载物料目录。
 */
function onEditSupplierChange() {
  editForm.details.forEach(item => { item.materialCode = ''; item.packType = ''; item.packCapacity = 0 })
  editMaterialOptions.value = {}
  loadEditSupplierCatalog(editForm.supplierCode)
}

function addEditDetail() {
  editForm.details.push({ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 })
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
      packType: '',
      packCapacity: d.packCapacity || 0,
      boxCount: (d.packCapacity > 0) ? Math.round((d.planQty || 0) / d.packCapacity) : 1
    }))
    if (editForm.details.length === 0) {
      editForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
    }
    // 重新获取器具信息
    for (let i = 0; i < editForm.details.length; i++) {
      if (editForm.details[i].materialCode) {
        await fetchEditPackCapacity(i, editForm.details[i].materialCode)
      }
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
      details: editForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount }))
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
const outMaterialOptions = ref({})
const outMaterialLoading = ref({})

const outboundForm = reactive({
  details: [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
})

function addOutDetail() {
  outboundForm.details.push({ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 })
}
function removeOutDetail(idx) {
  if (outboundForm.details.length > 1) outboundForm.details.splice(idx, 1)
}

function openOutboundDialog() {
  outboundForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
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

/**
 * 出库：当物料被选中时，从器具配置获取包装容量（使用物料默认供应商）。
 */
async function fetchOutPackCapacity(idx, materialCode) {
  if (!materialCode) {
    outboundForm.details[idx].packType = ''
    outboundForm.details[idx].packCapacity = 0
    return
  }
  try {
    const data = await getAppliances({ page: 1, size: 20, keyword: materialCode })
    const match = (data.records || []).find(a => a.materialCode === materialCode)
    if (match && match.packCapacity > 0) {
      outboundForm.details[idx].packType = match.packType || ''
      outboundForm.details[idx].packCapacity = match.packCapacity
    } else {
      outboundForm.details[idx].packType = ''
      outboundForm.details[idx].packCapacity = 0
    }
  } catch { /* */ }
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
    await createOutbound({ details: outboundForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount })) })
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
  details: [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
})

function addOutEditDetail() { outEditForm.details.push({ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }) }
function removeOutEditDetail(idx) { if (outEditForm.details.length > 1) outEditForm.details.splice(idx, 1) }

async function searchOutEditMaterials(query, idx) {
  outEditMaterialLoading.value[idx] = true
  try {
    const data = await getMaterials({ page: 1, size: 20, keyword: query || undefined })
    outEditMaterialOptions.value[idx] = data.records || []
  } catch { outEditMaterialOptions.value[idx] = [] }
  finally { outEditMaterialLoading.value[idx] = false }
}

/**
 * 出库编辑：当物料被选中时，从器具配置获取包装容量。
 */
async function fetchOutEditPackCapacity(idx, materialCode) {
  if (!materialCode) {
    outEditForm.details[idx].packType = ''
    outEditForm.details[idx].packCapacity = 0
    return
  }
  try {
    const data = await getAppliances({ page: 1, size: 20, keyword: materialCode })
    const match = (data.records || []).find(a => a.materialCode === materialCode)
    if (match && match.packCapacity > 0) {
      outEditForm.details[idx].packType = match.packType || ''
      outEditForm.details[idx].packCapacity = match.packCapacity
    } else {
      outEditForm.details[idx].packType = ''
      outEditForm.details[idx].packCapacity = 0
    }
  } catch { /* */ }
}

async function openOutEditDialog(row) {
  outEditTarget.value = row
  outEditSubmitting.value = false
  outEditMaterialOptions.value = {}
  try {
    const data = await getOutboundDetail(row.id)
    outEditForm.details = (data.details || []).map(d => ({
      materialCode: d.materialCode || '',
      packType: '',
      packCapacity: d.packCapacity || 0,
      boxCount: (d.packCapacity > 0) ? Math.round((d.planQty || 0) / d.packCapacity) : 1
    }))
    if (outEditForm.details.length === 0) {
      outEditForm.details = [{ materialCode: '', packType: '', packCapacity: 0, boxCount: 1 }]
    }
    // 重新获取器具信息
    for (let i = 0; i < outEditForm.details.length; i++) {
      if (outEditForm.details[i].materialCode) {
        await fetchOutEditPackCapacity(i, outEditForm.details[i].materialCode)
      }
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
    await updateOutbound(outEditTarget.value.id, { details: outEditForm.details.map(d => ({ materialCode: d.materialCode, boxCount: d.boxCount })) })
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
 * 将二维码输入框中的值添加为二维码标签。
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
  // 校验：每条明细的二维码折算数量应与本次出库数一致
  for (const row of outConfirmDetails.value) {
    if (row._confirmQty > 0 && (!row._barcodes || row._barcodes.length === 0)) {
      ElMessage.warning(`物料 ${row.materialCode} 本次出库数大于 0，请扫描或输入二维码`)
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
    ElMessage.error(err.message || '出库确认失败，请检查二维码是否正确且符合先进先出规则')
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

// ==================== 入库流水查询 ====================
const inboundFlowVisible = ref(false)
const inboundFlowLoading = ref(false)
const inboundFlowList = ref([])
const inboundFlowQuery = reactive({ orderNo: '', materialCode: '' })
// 缓存 inboundId → orderNo 映射
const inboundOrderNoMap = ref({})

function openInboundFlowDialog() {
  inboundFlowQuery.orderNo = ''
  inboundFlowQuery.materialCode = ''
  inboundFlowList.value = []
  inboundFlowVisible.value = true
}

async function loadInboundFlow() {
  inboundFlowLoading.value = true
  try {
    const params = { page: 1, size: 200 }
    if (inboundFlowQuery.orderNo.trim()) params.orderNo = inboundFlowQuery.orderNo.trim()
    if (inboundFlowQuery.materialCode.trim()) params.materialCode = inboundFlowQuery.materialCode.trim()
    const data = await getInboundFlow(params)
    inboundFlowList.value = data.records || []
    // 构建 inboundId → 单号缓存
    const ids = [...new Set(inboundFlowList.value.map(r => r.inboundId).filter(Boolean))]
    for (const id of ids) {
      if (!inboundOrderNoMap.value[id]) {
        try {
          const detail = await getInboundDetail(id)
          inboundOrderNoMap.value[id] = detail.orderNo
        } catch { inboundOrderNoMap.value[id] = '—' }
      }
    }
  } catch {
    inboundFlowList.value = []
  } finally {
    inboundFlowLoading.value = false
  }
}

function getInboundOrderNo(inboundId) {
  return inboundOrderNoMap.value[inboundId] || '加载中...'
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

// ==================== 物料目录加载 ====================

/** 加载供应商物料目录（含器具信息） — 支持单供应商回退 */
async function loadSupplierCatalog(supplierCode) {
  if (!supplierCode) { catalogMaterials.value = []; return }
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 200, supplierCode }),
      getAppliances({ page: 1, size: 500 })
    ])
    const materials = matRes.records || []
    const appliances = appRes.records || []
    catalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode && a.supplierCode === supplierCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _supplierCode: supplierCode,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
  } catch { catalogMaterials.value = [] }
}

/** 聚合加载所有选中供应商的物料目录 */
async function loadMultiSupplierCatalog() {
  const suppliers = inboundForm.selectedSuppliers
  if (suppliers.length === 0) { catalogMaterials.value = []; return }
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 500 }),
      getAppliances({ page: 1, size: 500 })
    ])
    const supplierSet = new Set(suppliers)
    const materials = (matRes.records || []).filter(m => supplierSet.has(m.supplierCode))
    const appliances = appRes.records || []
    catalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode && a.supplierCode === m.supplierCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _supplierCode: m.supplierCode,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
  } catch { catalogMaterials.value = [] }
}

async function loadEditSupplierCatalog(supplierCode) {
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 200, supplierCode }),
      getAppliances({ page: 1, size: 500 })
    ])
    const materials = matRes.records || []
    const appliances = appRes.records || []
    editCatalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode && a.supplierCode === supplierCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
  } catch {
    editCatalogMaterials.value = []
  }
}

/** 加载全部物料目录（出库用，无供应商筛选） */
async function loadAllCatalog() {
  try {
    const [matRes, appRes] = await Promise.all([
      getMaterials({ page: 1, size: 200 }),
      getAppliances({ page: 1, size: 500 })
    ])
    const materials = matRes.records || []
    const appliances = appRes.records || []
    outCatalogMaterials.value = materials.map(m => {
      const app = appliances.find(a => a.materialCode === m.materialCode)
      return {
        materialCode: m.materialCode,
        materialName: m.materialName,
        _appliance: !!app,
        _packType: app?.packType || '',
        _packCapacity: app?.packCapacity || 0
      }
    })
    outEditCatalogMaterials.value = outCatalogMaterials.value
  } catch {
    outCatalogMaterials.value = []
    outEditCatalogMaterials.value = []
  }
}

// ==================== 目录选择事件 ====================

function onCatalogSelect(val) { catalogChecked.value = val }
function onEditCatalogSelect(val) { editCatalogChecked.value = val }
function onOutCatalogSelect(val) { outCatalogChecked.value = val }
function onOutEditCatalogSelect(val) { outEditCatalogChecked.value = val }

function catalogSelectable(row) {
  // 未配置器具的物料不可选
  if (!row._appliance) return false
  // 已存在于明细列表的物料不可重复选
  const exists = inboundForm.details.some(d => d.materialCode === row.materialCode)
  return !exists
}
function editCatalogSelectable(row) {
  if (!row._appliance) return false
  const exists = editForm.details.some(d => d.materialCode === row.materialCode)
  return !exists
}
function outCatalogSelectable(row) {
  if (!row._appliance) return false
  const exists = outboundForm.details.some(d => d.materialCode === row.materialCode)
  return !exists
}
function outEditCatalogSelectable(row) {
  if (!row._appliance) return false
  const exists = outEditForm.details.some(d => d.materialCode === row.materialCode)
  return !exists
}

function selectAllCatalog() { catalogTableRef.value?.toggleAllSelection() }
function invertCatalog() {
  const table = catalogTableRef.value
  if (!table) return
  filteredCatalog.value.forEach(row => {
    if (catalogSelectable(row)) table.toggleRowSelection(row, !catalogChecked.value.includes(row))
  })
}
function selectAllEditCatalog() { editCatalogTableRef.value?.toggleAllSelection() }
function invertEditCatalog() {
  const table = editCatalogTableRef.value
  if (!table) return
  filteredEditCatalog.value.forEach(row => {
    if (editCatalogSelectable(row)) table.toggleRowSelection(row, !editCatalogChecked.value.includes(row))
  })
}
function selectAllOutCatalog() { outCatalogTableRef.value?.toggleAllSelection() }
function invertOutCatalog() {
  const table = outCatalogTableRef.value
  if (!table) return
  filteredOutCatalog.value.forEach(row => {
    if (outCatalogSelectable(row)) table.toggleRowSelection(row, !outCatalogChecked.value.includes(row))
  })
}
function selectAllOutEditCatalog() { outEditCatalogTableRef.value?.toggleAllSelection() }
function invertOutEditCatalog() {
  const table = outEditCatalogTableRef.value
  if (!table) return
  filteredOutEditCatalog.value.forEach(row => {
    if (outEditCatalogSelectable(row)) table.toggleRowSelection(row, !outEditCatalogChecked.value.includes(row))
  })
}

// ==================== 批量添加 ====================

function batchAddToInbound() {
  for (const row of catalogChecked.value) {
    if (!row._appliance) continue
    const exists = inboundForm.details.some(d => d.materialCode === row.materialCode)
    if (exists) continue
    // 找到第一个空白行或追加
    const emptyIdx = inboundForm.details.findIndex(d => !d.materialCode)
    if (emptyIdx >= 0) {
      inboundForm.details[emptyIdx] = { materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 }
    } else {
      inboundForm.details.push({ materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 })
    }
  }
  catalogTableRef.value?.clearSelection()
  ElMessage.success(`已添加 ${catalogChecked.value.length} 种物料`)
}

function batchAddToEditInbound() {
  for (const row of editCatalogChecked.value) {
    if (!row._appliance) continue
    const exists = editForm.details.some(d => d.materialCode === row.materialCode)
    if (exists) continue
    const emptyIdx = editForm.details.findIndex(d => !d.materialCode)
    if (emptyIdx >= 0) {
      editForm.details[emptyIdx] = { materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 }
    } else {
      editForm.details.push({ materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 })
    }
  }
  editCatalogTableRef.value?.clearSelection()
  ElMessage.success(`已添加 ${editCatalogChecked.value.length} 种物料`)
}

function batchAddToOutbound() {
  for (const row of outCatalogChecked.value) {
    if (!row._appliance) continue
    const exists = outboundForm.details.some(d => d.materialCode === row.materialCode)
    if (exists) continue
    const emptyIdx = outboundForm.details.findIndex(d => !d.materialCode)
    if (emptyIdx >= 0) {
      outboundForm.details[emptyIdx] = { materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 }
    } else {
      outboundForm.details.push({ materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 })
    }
  }
  outCatalogTableRef.value?.clearSelection()
  ElMessage.success(`已添加 ${outCatalogChecked.value.length} 种物料`)
}

function batchAddToOutEdit() {
  for (const row of outEditCatalogChecked.value) {
    if (!row._appliance) continue
    const exists = outEditForm.details.some(d => d.materialCode === row.materialCode)
    if (exists) continue
    const emptyIdx = outEditForm.details.findIndex(d => !d.materialCode)
    if (emptyIdx >= 0) {
      outEditForm.details[emptyIdx] = { materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 }
    } else {
      outEditForm.details.push({ materialCode: row.materialCode, packType: row._packType, packCapacity: row._packCapacity, boxCount: 1 })
    }
  }
  outEditCatalogTableRef.value?.clearSelection()
  ElMessage.success(`已添加 ${outEditCatalogChecked.value.length} 种物料`)
}

// ==================== 统一箱数 & 清空 ====================

function applyUniformBoxCount(formType) {
  const count = {
    inbound: uniformBoxCount.value,
    editInbound: editUniformBoxCount.value,
    outbound: outUniformBoxCount.value,
    outEdit: outEditUniformBoxCount.value
  }[formType] || 1
  const details = {
    inbound: inboundForm.details,
    editInbound: editForm.details,
    outbound: outboundForm.details,
    outEdit: outEditForm.details
  }[formType] || []
  let applied = 0
  details.forEach(d => { if (d.materialCode) { d.boxCount = count; applied++ } })
  ElMessage.success(`已将 ${applied} 行箱数统一设为 ${count}`)
}

function clearAllDetails(formType) {
  const details = {
    inbound: inboundForm.details,
    editInbound: editForm.details,
    outbound: outboundForm.details,
    outEdit: outEditForm.details
  }[formType] || []
  details.splice(0, details.length, { materialCode: '', packType: '', packCapacity: 0, boxCount: 1 })
}

/** 计算单行总数 */
function calcRowTotal(item) {
  if (!item.materialCode) return '—'
  return (item.boxCount || 0) * (item.packCapacity || 0) || '—'
}

// ==================== 对话框打开钩子 ====================

function onCreateDialogOpened() {
  if (inboundForm.selectedSuppliers.length > 0) {
    loadMultiSupplierCatalog()
  }
}

function onEditDialogOpened() {
  if (editForm.supplierCode) {
    loadEditSupplierCatalog(editForm.supplierCode)
  }
}

function onOutDialogOpened() {
  loadAllCatalog()
}

function onOutEditDialogOpened() {
  loadAllCatalog()
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
  grid-template-columns: minmax(160px, 1fr) 100px 90px 110px 110px 74px;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.detail-head-6 {
  grid-template-columns: minmax(140px, 1fr) 90px 80px 100px 100px 74px;
}
.detail-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 100px 90px 110px 110px 74px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 10px;
}
.detail-row-6 {
  grid-template-columns: minmax(140px, 1fr) 90px 80px 100px 100px 74px;
}
.detail-row :deep(.el-input-number) {
  width: 100%;
}
/* 只读单元格：器具类型、单箱容量、总数 */
.readonly-cell {
  font-size: 13px;
  color: var(--text-primary);
  padding: 4px 8px;
  background: #f5f7fa;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.total-cell {
  font-weight: 600;
  color: var(--wms-primary);
  background: #ecf5ff;
  border-color: #d9ecff;
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
  position: relative;
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
  .history-stats,
  .dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-stats {
    flex-wrap: wrap;
  }
  .detail-head, .detail-head-6, .detail-head-7 {
    display: none;
  }
  .detail-row, .detail-row-6, .detail-row-7 {
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
.supplier-code-text {
  font-size: 11px;
  color: var(--text-secondary);
}

/* ==================== 物料目录面板 ==================== */
.catalog-panel {
  border: 1px solid var(--border-light);
  border-radius: 4px;
  overflow: hidden;
}
.catalog-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid var(--border-light);
}
.catalog-hint {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}
.catalog-scroll {
  overflow-y: auto;
}
.catalog-actions {
  padding: 8px 12px;
  border-top: 1px solid var(--border-light);
  background: #fafafa;
}

/* ==================== 批量工具栏 ==================== */
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: #f0f5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.batch-label {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.batch-summary {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: auto;
}
.batch-summary b {
  color: var(--wms-primary);
}

/* ==================== 明细列表区（抽屉自带滚动，此处不设 max-height） ==================== */
.detail-scroll {
  border: 1px solid var(--border-light);
  border-radius: 4px;
  padding: 6px;
}

/* ==================== 序号列 ==================== */
.col-seq {
  width: 28px;
  flex-shrink: 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-secondary);
}
.seq-num {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  background: #f5f7fa;
  border-radius: 4px;
  font-weight: 500;
  color: var(--text-secondary);
  font-size: 12px;
}

/* 7列网格 */
.detail-head-7 {
  grid-template-columns: 28px minmax(120px, 1fr) 80px 80px 100px 100px 56px;
}
.detail-row-7 {
  grid-template-columns: 28px minmax(120px, 1fr) 80px 80px 100px 100px 56px;
}

/* ==================== 辅助 ==================== */
.text-warn {
  color: var(--wms-warning);
  font-weight: 500;
}

/* ==================== 出库二维码输入区 ==================== */
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

