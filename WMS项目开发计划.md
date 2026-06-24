# WMS 项目完成度评估与开发计划

> 评估日期：2026-06-23

## 一、总体评分

| 模块 | 完成度 | 状态 |
|------|--------|------|
| 入库功能 | 90% | 🟢 基本完成，缺删除/部分入库/ASN |
| 出库功能 | 85% | 🟢 基本完成，整箱FIFO可用，流出库链路追踪丢失 |
| **封存解封** | **0%** | 🔴 完全未开发 |
| **手机端** | **30%** | 🟡 仅有响应式CSS，无PWA/独立移动端 |
| 库存监控 | 75% | 🟡 可用但 dailyConsume 硬编码 |
| 看板监控 | 80% | 🟢 Dashboard功能丰富，缺WebSocket实时推送 |
| 出入库历史 | 70% | 🟡 入库历史完整，出库历史仅模态框 |
| 先进先出(FIFO) | 85% | 🟢 整箱FIFO已实现 |
| **AI功能** | **35%** | 🔴 LLM调用为桩代码，仅Mock引擎工作 |
| 权限管理 | 60% | 🟡 后端完整，前端无管理界面 |
| 供应商/物料/器具 | 85% | 🟢 CRUD完整，缺批量导入 |
| 库存追溯 | 80% | 🟢 基本可用，缺时间线视图 |

## 二、待开发模块详细计划

### 2.1 封存解封（优先级：高，新模块）

**概念：** 对物料或批次执行封存（暂停流转）/解封（恢复流转）。

**后端：**
- 新增 `InventoryFreeze` 实体（freezeId, materialCode, barcodeId, freezeType, reason, operator, freezeTime, unfreezeTime, status）
- Barcode 实体新增 `status` 枚举值 `FROZEN`
- `FreezeController`：POST `/api/freeze/seal`（封存）、POST `/api/freeze/unseal`（解封）、GET `/api/freeze/list`（封存记录）
- 封存时校验：在库状态的条码才能封存；封存后该条码不可被出库挑选
- 解封时恢复为"在库"
- 出库 FIFO 逻辑中跳过 `status=FROZEN` 的条码

**前端：**
- `FreezeManagement.vue`：封存记录列表 + 封存/解封操作对话框

### 2.2 手机端（优先级：中）

**方案选择：**
- 方案A：PWA（添加到主屏幕、离线缓存、推送通知）— 改动小
- 方案B：独立移动端应用（UniApp / H5+）— 体验好但工作量大
- **推荐方案A**：基于现有响应式CSS，增加 PWA 支持

**实施：**
- `manifest.json` + `service-worker.js`（vite-plugin-pwa）
- 优化触摸交互（按钮间距、手势滑动）
- 关键页面移动端优先重排（Dashboard、扫码入库、入库确认）
- 扫码功能已在移动端可用（html5-qrcode rear camera）

### 2.3 AI功能完善（优先级：高）

**当前状态：** LLM调用为桩代码，`executeAsynchronousPredict()` 直接调 `RuleMockEngine`，配置的 `api-key` 和 `base-url` 未使用。

**待实施：**
1. 实现真实 LLM HTTP 调用（OpenAI-compatible Chat Completion API）
2. 构建 Prompt 模板——组装库存快照 + 近30日出库流水 + 物料档案 → JSON → 解析 AiReport
3. `dailyConsume` 改为从真实出库流水动态计算（不再硬编码 10.0）
4. 增加批量 AI 分析和定时 AI 分析
5. AI 报告列表页（目前只能查最新一份）

### 2.4 出库历史独立页面（优先级：中）

- 新建 `OutboundHistory.vue` 完整页面（含汇总统计、日期筛选、趋势图、CSV导出）
- 修复出库流水 `inboundId=0L` 的链路追踪丢失问题

### 2.5 权限管理前端（优先级：中）

- 新建 `UserManagement.vue` + `RoleManagement.vue` 管理界面
- 后端 API 已完整，仅缺前端

### 2.6 看板增强（优先级：低）

- WebSocket 实时推送替代轮询
- 可配置告警阈值

## 三、已知Bug修复

| # | 问题 | 严重程度 | 状态 |
|---|------|---------|------|
| 1 | `StockServiceImpl.dailyConsume` 硬编码 10.0 | 中 | 待修复 |
| 2 | 出库流水 `inboundId=0L`，追溯链路丢失 | 高 | 待修复 |
| 3 | `InboundController` 无删除端点 | 低 | 待新增 |
| 4 | `batchCreate` 无事务回滚 | 中 | 待修复 |
| 5 | `InboundHistoryController` Bean名冲突 | 高 | 待修复 |

## 四、AI融合方向建议

**推荐方向：内部需求预测 + AI波动监控**

基于现有架构（已有 AiReport 实体、异步分析流水线、Mock引擎），推荐：
1. **AI需求预测**：从出库历史计算实际日均消耗（替代硬编码10.0），送入LLM做趋势预测
2. **AI波动监控**：定时分析库存变化率，检测异常波动（突增/突降），主动推送预警
3. **AI补货建议**：结合当前库存、日均消耗、供应商交货周期，生成补货计划
4. **AI异常检测**：对出入库操作日志做异常模式识别（如短时间内大量出库）

## 五、实施顺序建议

| 阶段 | 内容 | 预计工作量 |
|------|------|-----------|
| **Week 1** | Bug修复（#1-#5）+ 封存解封后端 | 2天 |
| **Week 1** | 封存解封前端 + FIFO联动 | 1天 |
| **Week 2** | AI LLM真实对接 + dailyConsume动态计算 | 2天 |
| **Week 2** | 出库历史独立页面 + 链路追踪修复 | 1天 |
| **Week 3** | 手机端PWA + 权限管理前端 | 2天 |
| **Week 3** | 看板WebSocket + 测试联调 | 1天 |
