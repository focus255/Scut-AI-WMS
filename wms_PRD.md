# 智库WMS — 智能仓储管理系统 PRD

> **版本**: v1.0  
> **最后更新**: 2026-06-03  
> **作者**: 智库WMS 开发团队  

---

## 目录

1. [项目概述](#1-项目概述)
2. [总体架构设计](#2-总体架构设计)
3. [核心业务流程](#3-核心业务流程)
4. [功能说明](#4-功能说明)
5. [页面 / 模块说明](#5-页面--模块说明)
6. [系统详细设计](#6-系统详细设计)
7. [接口设计](#7-接口设计)
8. [数据库设计](#8-数据库设计)
9. [异常处理](#9-异常处理)
10. [关键日志 / 事件](#10-关键日志--事件)
11. [安全细节](#11-安全细节)
12. [技术栈](#12-技术栈)
13. [部署说明](#13-部署说明)
14. [演示场景](#14-演示场景)
15. [验收标准](#15-验收标准)

---

## 1. 项目概述

### 1.1 项目名称

**智库WMS**（Smart Inventory Warehouse Management System）

### 1.2 项目简介

智库WMS 是一个面向制造与流通企业仓储规划及运维人员的智能仓储管理系统。系统在传统 WMS 仓储流转业务（基础信息、入库管理、出库管理、库存报表、高低储预警）的基础上，融入大语言模型（LLM）技术。

通过定时任务或用户手动触发，系统将现有库存状态、高低储阈值、近期出入库流转频次及导入的下游物料需求数据转化为特定的 Prompt，调用大语言模型进行智能库存风险预测（如低储断供风险、长期滞销风险等），并自动生成实用的补货建议与异常物料分析报告，助力企业仓储管理从 **"被动响应"向"主动规划"** 升级。

### 1.3 项目目标

| 目标 | 说明 |
| :--- | :--- |
| **夯实 WMS 基础能力** | 实现物料、器具包装、供应商等基础数据的增删改查，支持完整的入库、出库、二维码流转全链路手工及单据操作。 |
| **精细化库存水位控制** | 支持物料高储、低储天数的精细化配置，并在动态库存报表中直观呈现水位状态。 |
| **融合大语言模型智能化预测** | 对接大语言模型 API，实现库存断供风险和滞销积压风险的智能预测。 |
| **自动生成决策报告** | 基于 AI 分析结果，自动产出结构化的补货建议与异常物料分析报告，支持一键转补货计划。 |
| **高可用与异步兜底设计** | 采用异步线程池处理 AI 调用任务，提供标准规则 Mock 兜底机制，确保大模型 API 超时或故障时系统主业务不受阻。 |
| **工程化敏捷落地** | 基于标准实训技术栈（Spring Boot + Vite + Element Plus + MySQL）进行务实开发，保障系统轻量化部署与极佳的交互体验。 |

### 1.4 目标用户

| 角色 | 职责描述 |
| :--- | :--- |
| **仓储主管 / 规划员** | 负责高低储规则维护、库存报表审阅，深度使用 AI 风险预测与补货建议功能进行采购与库存调配决策。 |
| **仓库操作员** | 负责日常入库单制作、物料二维码打印、手工出入库核销等现场作业。 |

---

## 2. 总体架构设计

### 2.1 系统架构图

```text
┌─────────────────────────────────────────────────────────────────┐
│                 前端展现层 (Vue 3 + Element Plus + Vite)          │
│  [登录页]    [智能看板首页]    [物料基础页]    [入出库作业]    [库存预警报告页] │
└─────────────────────────────────────────────────────────────────┘
                                   │
                     HTTP / JSON (Axios) + JWT
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                 核心后端层 (Spring Boot 3.x 单体架构)              │
│                                                                   │
│  [安全鉴权拦截] → Spring Boot Interceptor (JWT 验证、请求上下文解析) │
│                                                                   │
│  [控制层 Controller] → 接收前端请求、统一接口返回格式 ({code, msg, data}) │
│                                                                   │
│  [业务服务层 Service]                                              │
│   ├── UserService (用户登录、预置账号校验)                          │
│   ├── BaseInfoService (物料、器具包装、供应商档案维护)              │
│   ├── InoutService (入库单生成、二维码打印、出库单核销、库存动态增减)  │
│   ├── StockService (库存报表多维检索、高低储水位动态计算)           │
│   └── LLMIdentifyService (大模型Prompt动态拼装、异步线程池调度、Mock兜底) │
│                                                                   │
│  [持久层 Mapper] → MyBatis-Plus 接口映射                           │
└─────────────────────────────────────────────────────────────────┘
                    │                               │
          MyBatis-Plus                         HTTP / POST (JSON)
                    │                               │
                    ▼                               ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│        持久化数据层           │    │         外部 AI 能力层         │
│  MySQL 8.0 (单实例单数据库)   │    │  大语言模型 API               │
│  11 张业务表                 │    │  (通义千问 / DeepSeek 等       │
│                              │    │   标准 Chat 接口)             │
└──────────────────────────────┘    └──────────────────────────────┘
```

### 2.2 模块划分

| 模块名称 | 主要职责 |
| :--- | :--- |
| **auth-component** | 负责系统用户的登录认证、密码 BCrypt 加密、JWT 生成与统一拦截校验。 |
| **baseinfo-component** | 维护仓库核心静态主数据，包括物料表、器具表、供应商表，提供基础增删改查。 |
| **wms-core-component** | 承载核心仓储物流流转，负责入库单（及明细）、出库单（及明细）的手工创建与状态变更，绑定二维码表实现库存扣减。 |
| **stock-alarm-component** | 结合高低储天数参数与零件需求导入，动态计算并输出实时库存报表与高低储预警标记。 |
| **ai-intelligence-component** | 智能预测核心。负责拼装包含库存快照与未来需求的 Prompt，通过线程池执行异步调用 LLM，解析并回写库存风险分析与补货报告。提供大模型不可用时的 Mock 规则引擎。 |

---

## 3. 核心业务流程

### 3.1 用户登录与鉴权流程

1. 用户在前端登录界面输入预置的账号及密码。
2. 前端向后端发送明文登录请求。
3. 后端业务模块截获请求，查询 `users` 表，利用 BCrypt 对比密码哈希值。
4. 校验成功后，后端组装用户基础信息并生成附带 **2 小时有效期**的 JWT 令牌返回前端。
5. 前端将 JWT 缓存在 `localStorage` 中，后续的所有 Axios 请求均通过 `Authorization` 请求头自动携带该 Token。
6. 后端配置统一的 `HandlerInterceptor` 拦截器，除 `/api/auth/login` 白名单外，其余接口若无 Token 或 Token 过期统一拒绝，返回 `401` 错误码。

### 3.2 基础入出库与库存扣减流程

#### 入库流转

1. 选择供应商与零件，创建入库单，系统生成 `inbound_orders` 记录，状态默认为 **"未入库"**。
2. 自动或手工根据器具包装容量拆分并生成物料对应的二维码记录存入 `barcodes` 表。
3. 操作员执行"手工入库确认"，系统更新 `inbound_details` 中的实际入库数量。
4. 当明细全部核销，入库单更新为 **"已完成"**，同时原子性地增加 `inventories` 表中的实物库存量。

#### 出库流转

1. 导入零件未来需求或选择具体零件，填写计划出库数量，生成出库单（状态为 **"未出库"**）。
2. 仓库作业人员按先进先出（FIFO）或指定物料二维码核销，输入实际出库数。
3. 确认出库后，出库单状态变更为 **"已完成"**，系统同步扣减 `inventories` 表中对应的库存余额。

### 3.3 高低储与滞销风险判断流程

| 规则类型 | 触发条件 | 报表表现 |
| :--- | :--- | :--- |
| **低储预警** | 当前库存数量 < 日均消耗量 × 低储天数 | 红字预警 |
| **高储积压** | 当前库存数量 > 日均消耗量 × 高储天数 | 黄字预警 |
| **滞销风险** | 库存周转天数（当前库存 / 近30日日均出库数）超过 90 天，且近 15 天无出库记录 | 标记为"潜在滞销物料" |

> 上述标准规则判断结果将作为基础事实，一并输入给 AI 进行深度分析。

### 3.4 AI 库存风险预测与异步报告生成流程

为保证前端操作响应极速，AI 库存风险预测采用**异步执行机制**：

```text
[前端 / 定时器触发 AI 预测请求]
              │
              ▼
[后端接收请求: 组装基本数据快照]
              │
              ▼
[向数据库创建一条 AI 报告记录] → 状态初始化为: PENDING (等待分析)
              │
              ├──────────────────────────────────────┐
              │ (立即返回前端: Code=0, 提示任务已启动)  │
              ▼ (交由后端 TaskExecutor 线程池异步处理)  ▼
[状态更新为: RUNNING (分析中)]              [前端开启轮询或进入列表查阅]
              │
              ▼
        [组装 AI Prompt]
              │
              ▼
    (调用大语言模型标准接口)
              │
      ┌───────┴───────┐
      │               │
  [调用成功]      [调用失败/超时/格式错]
      │               │
      ▼               ▼
[解析标准 JSON]   [触发规则引擎 Mock 兜底]
      │               │
      ▼               ▼
[回写 AI 分析报告] [回写规则生成的标准 Mock 报告]
      │               │
      └───────┬───────┘
              │
              ▼
[状态更新为: SUCCESS / MOCKED]
              │
              ▼
[前端轮询结束 / 刷新展现完整的智能补货建议报告]
```

---

## 4. 功能说明

### 4.1 用户登录功能

| 功能名称 | 输入 | 处理逻辑 | 输出 / 效果 |
| :--- | :--- | :--- | :--- |
| 用户登录 | 账号、密码 | 拦截器放行，查询用户表，BCrypt 比对哈希值，生成包含用户 ID 的 JWT。 | 成功返回 `token` 及过期秒数；失败返回 400（凭据错误）。 |
| 登录鉴权拦截 | 请求头携带的 JWT | 拦截除登录外的全部接口，利用密钥解析 Token 合法性、时效性。 | 合法放行并注入线程上下文；非法返回 `401`。 |
| 用户登出 | 无 | 前端销毁浏览器缓存的 Token。 | 页面重定向至登录页，后续请求失效。 |

### 4.2 基础信息管理功能

- **物料主数据维护**：支持对工厂内周转的物料进行基础建档，包含物料号（唯一键）、物料名称、默认供应商。
- **器具 / 包装管理**：定义不同物料对应的标准工位器具。字段包括器具型号、单包装容量（如 1 箱装 20 个零件），用以指导入库时二维码的自动拆分规则。
- **供应商档案维护**：统一维护配套供应商数据，包括供应商代码（唯一键）、供应商全称。

### 4.3 入出库及库存管理功能

- **手工入库流转**：创建入库单 → 增加物料明细（计划入库数） → 确认入库（回填实际入库数，二维码变为"在库"状态） → 动态刷新对应物料的物理库存。
- **手工出库作业**：创建出库单 → 关联零件与计划出库数 → 现场出库确认（核销对应二维码或扣减总数） → 扣减对应物料库存。
- **零件需求导入**：支持通过前端组件或模拟文本导入未来 7 天或 15 天的下游整车/总成"零件预测需求数"，用以提供给库存报表及 AI 进行断供风险的深度推演。
- **动态库存报表**：综合物料、供应商、当前物理库存、已维护的高低储天数进行集中多维展示。

### 4.4 高低储预警与风险规则配置

- **水位规则设定**：可在系统内统一针对各物料维护其"安全低储天数"与"安全高储天数"（例如低储 3 天，高储 15 天）。
- **内置规则扫描**：系统提供内置统计逻辑，可直接通过常规 SQL / 代码计算输出基础的预警标识（正常、超高储、超低储、滞销），为 AI 提示词提供量化事实支撑。

### 4.5 AI 库存风险预测功能（含 Prompt 设计）

**功能描述**：点击"一键 AI 风险推演"，系统调取当前物料的动态数据，拼装出包含业务场景、事实约束与输出格式期望的深度提示词，提交给 LLM 进行库存健康度"会诊"。

**AI 预测提示词（Prompt）结构设计**：

```text
你是一位资深的汽车物流供应链专家与精益仓储规划师。请对以下物料的库存风险进行深度推演分析。

【已知基础事实】:

物料号与名称: ${materialCode}(${materialName})

配套供应商: ${supplierName}

当前物理库存量: ${currentStock} 件

单器具包装容量: ${packCapacity} 件/箱

安全库存配置: 低储天数标准为 ${minStockDays} 天，高储天数标准为 ${maxStockDays} 天。

近30日历史出库统计: 总出库量 ${totalOutbound30} 件，日均消耗量 ${dailyConsume} 件。

近15天库龄表现: 产生出库活跃的天数为 ${activeDays} 天。

导入的未来15天确定性下游零件生产需求流预测: ${demandForecastJson}

【请结合上述事实进行推演并评估如下风险】:

1. 低储断供风险：结合未来排产需求与日变动，推演库存何时可能耗尽，是否存在断供红线。
2. 滞销积压风险：结合库龄、历史出库频次与当前高储标准，评估是否存在资金占用与呆滞风险。

【必须且只能以下列 JSON 结构格式化输出，不要包含任何前导、后导解释文本或 Markdown 标记】:
```

**期望输出 JSON 结构**：

```json
{
  "riskType": "LOW_STOCK",
  "riskLevel": "CRITICAL",
  "analysisContent": "详细根因推演与风险阐述内容...",
  "replenishmentSuggestion": "具体的补货方案，包括建议补货箱数、紧急到货时间等描述...",
  "suggestedQty": 340,
  "confidence": 0.92
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `riskType` | String | 风险类型：`NORMAL`（正常）、`LOW_STOCK`（断供预警）、`DEAD_STOCK`（滞销风险）、`BOTH`（双重风险） |
| `riskLevel` | String | 风险等级：`LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |
| `analysisContent` | String | 详细根因推演与风险阐述内容 |
| `replenishmentSuggestion` | String | 具体的补货方案描述，包括建议补货箱数、紧急到货时间等 |
| `suggestedQty` | Integer | 建议具体的补货数量（件），若为滞销风险则建议为 0 |
| `confidence` | Float | 置信度评分，范围 0.0 ~ 1.0 |

### 4.6 AI 补货建议与异常物料报告自动生成功能

- **报告回写与展示**：大模型生成的结构化 JSON 经后端解析后，将自动填入 `ai_inventory_reports` 表。前端提供精美的卡片式"智能 AI 报告详页"，突出显示 AI 评定的风险等级、趋势分析文本。
- **补货一键转化**：若 AI 报告中包含 `suggestedQty > 0` 且风险类型为 `LOW_STOCK` 或 `BOTH`，前端页面报告右上方提供 **"一键生成补货入库单"** 按钮。点击后，系统自动提取 AI 建议的物料、供应商及数量，一键初始化一张"未入库"状态的手工入库单，实现业务闭环。

### 4.7 接口返回格式统一

系统所有控制层接口必须采用统一的泛型数据包装结构，严禁返回裸数据。

#### 标准成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "reportId": 20261005,
    "riskType": "LOW_STOCK",
    "predictionStatus": "SUCCESS"
  }
}
```

#### 标准错误响应

```json
{
  "code": 2002,
  "message": "AI大模型接口服务超时，系统已启动精益规则引擎实施标准降级决策方案",
  "data": null
}
```

#### 全局核心错误码字典

| 错误码 (code) | 语义解释 | 处理对策 |
| :--- | :--- | :--- |
| `0` | 成功 | 前端正常渲染 |
| `400` | 请求参数有误 / 校验失败 | 前端 `ElMessage.warning` 提示传入不合法 |
| `401` | 未登录或 JWT 令牌失效 | 路由守卫拦截，强制跳转至登录页 |
| `404` | 资源（物料、单据）不存在 | 提示找不到相关记录 |
| `500` | 后端系统运行时未知异常 | 触发全局异常处理器，捕获并打印堆栈 |
| `2001` | AI 分析引擎线程池满或拒绝提交 | 引导用户稍后重试 |
| `2002` | AI 大模型 API 调用超时/网络中断，已触发标准规则 Mock 兜底 | 报告状态标为 `MOCKED`，利用本地规则组装基础建议供用户查阅 |
| `3001` | 库存扣减失败（如出库数大于现有物理库存） | 阻止单据保存，提示"物理库存余额不足" |

---

## 5. 页面 / 模块说明

### 5.1 登录页

- **UI 组件**：居中卡片布局，包含系统 LOGO、系统名称"智库WMS - 智能仓储管理系统"、`el-input` 账号输入框、`el-input(type="password")` 密码输入框、`el-button` 登录按钮。
- **交互逻辑**：点击登录触发后台校验，若密码不正确，输入框下方浮现红字"用户名或密码错误"；登录成功后静默写入 `localStorage` 并跳转至看板首页。

### 5.2 智能库存看板首页（BI + AI 结合）

**顶部指标卡片**：

| 指标 | 说明 |
| :--- | :--- |
| 总物料 SKU 数 | 系统中物料档案总数（个） |
| 物理异常呆滞物料数 | 当前标记为呆滞的物料数（个） |
| 断供高风险物料数 | 提取最近一次 AI 评估为断供高风险的物料数（个） |

**中部双栏布局**：

| 左栏 | 右栏 |
| :--- | :--- |
| 表格形式展示当前库存水位分布，高低储违规物料一目了然。 | 嵌入"AI 智能物料直通速查"简易对话框。操作员输入特定零件号，点击"小智快查"，系统直接拉取或实时触发该物料的最新 AI 智能库存报告摘要。 |

### 5.3 物料与基础数据管理页

- **页面内容**：包含三个子 Tab（物料档案、器具配置、供应商库）。
- **操作功能**：支持 Element Plus 的 `el-table` 进行基础的分页展示，每一行末尾提供"编辑"、"删除"按钮；顶部提供"新增"按钮，弹出对话框完成静态档案数据录入。

### 5.4 入库与出库管理页

#### 入库管理

- 列表页展示入库单号、供应商、单据状态（未入库、已完成）、创建时间。
- 点击"新建入库单"，通过下拉菜单选择供应商，动态添加物料并输入计划数。
- 提供"手工确认入库"操作，点击确认后改变状态并入库。

#### 出库管理

- 支持制作手工出库单，提供"零件预测需求导入"的快捷入口（文本域或模拟 CSV 格式数据贴入），用以刷新系统内部的物料短期生产消耗期望。

### 5.5 库存报表与风险预警页

- **组合多维列表**：字段包含物料号、物料名称、当前物理库存、高储阈值、低储阈值、未来 15 天预测总消耗、内置规则评级。
- **高亮规则**：
  - 超低储行 → 物料背景或字体显示为**浅红色**
  - 超高储行 → 显示为**浅黄色**
- **行内核心按钮**：每行末尾提供"触发 AI 深度推演"操作，点击后对应行内的分析状态变为转圈加载中。

### 5.6 AI 风险预测与智能报告详页

**报告详情视图**：

| 区域 | 内容 |
| :--- | :--- |
| **基本属性区** | 显示报告编号、物料基本资料、诊断触发时刻、诊断执行状态（`PENDING` / `RUNNING` / `SUCCESS` / `MOCKED`）。 |
| **AI 核心诊断区** | 通过卡片展示大模型返回的结构化推演结论。包括【风险判定结论】（带不同色标标签）、【根因分析内容】（大段详细文字）、【置信度评分】。 |
| **精益行动建议区** | 突出显示 AI 给出的【智能补货方案指导】。 |
| **业务关联行动** | 若判定为严重低储，右上角闪烁显示 **"采纳建议：一键转标准入库单"** 按钮。用户点击后，页面无缝跳转至入库单录入页，且 AI 给出的建议补货数量自动填充到表单中。 |

---

## 6. 系统详细设计

### 6.1 统一安全鉴权拦截器（Interceptor）

**职责**：作为后端单体 Spring Boot 系统的防火墙。

**技术细节**：

- 编写 `JwtInterceptor` 继承 `HandlerInterceptor`。
- 在 `preHandle` 方法中获取 HTTP Header 中的 `Authorization` 字段。
- 若不存在或不满足 `Bearer ` 前缀，直接利用 `response.getWriter().write()` 返回标准的统一错误 JSON（`code: 401`）。
- 若合法，则解析出 JWT 中的 `userId`，将其绑定到本地线程变量 `BaseContext.setCurrentId(userId)` 中，便于后续 Service 层与 Mapper 层在插入或更新时自动补充 `created_by` 和 `updated_by` 审计字段。

### 6.2 仓储核心服务层层分层设计

| 服务 | 说明 |
| :--- | :--- |
| **MaterialService** | 提供带有条件构造器（`QueryWrapper`）的分页查询。 |
| **InboundService & OutboundService** | 使用 Spring 的 `@Transactional(rollbackFor = Exception.class)` 强事务注解。保证更新单据明细状态、新增二维码明细与扣减/增加 `inventories` 中的物理库存数量在同一个 MySQL 事务中执行。任何一步失败（如出库发生超扣、负库存违规）全面回滚。 |

### 6.3 AI 智能预测引擎模块（Asynchronous Engine）

#### 线程池配置

| 参数 | 值 |
| :--- | :--- |
| 核心线程数 | 4 |
| 最大线程数 | 8 |
| 队列容量 | 100 |
| 拒绝策略 | `CallerRunsPolicy` |

#### 异步方法编排

```java
@Service
public class LLMIdentifyServiceImpl implements LLMIdentifyService {

    @Async("aiTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void executeAsynchronousPredict(Long reportId, Long materialId) {
        // 1. 将报告表对应记录状态变更为 RUNNING
        // 2. 抽取物料历史出库、未来预测需求流等事实，动态格式化拼装大模型 Prompt
        // 3. 发起标准 HTTP POST 请求调用大模型 API，设置严格连接超时 3 秒，读取超时 10 秒
        // 4. try-catch 捕获网络层及大模型端返回的所有异常
        // 5. 若成功：解析标准的 JSON 字符串，反序列化后更新报告，状态置为 SUCCESS
        // 6. 若失败/超时：捕捉异常，转而调用本地规则降级引擎 (RuleMockEngine)，
        //    计算出标准建议，状态置为 MOCKED
    }
}
```

---

## 7. 接口设计

### 7.1 用户接口

#### 7.1.1 用户登录

| 属性 | 值 |
| :--- | :--- |
| **URL** | `/api/auth/login` |
| **Method** | `POST` |

**请求体参数**：

```json
{
  "username": "wms_planner",
  "password": "secure_password_123"
}
```

**返回数据示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200
  }
}
```

### 7.2 基础信息及入出库接口

#### 7.2.1 新建入库单

| 属性 | 值 |
| :--- | :--- |
| **URL** | `/api/inbound/orders` |
| **Method** | `POST` |

**请求体参数**：

```json
{
  "supplierCode": "SUP_VWG_09",
  "details": [
    {
      "materialCode": "M_PART_001",
      "packCapacity": 20,
      "planQty": 200
    }
  ]
}
```

**返回数据示例**：

```json
{
  "code": 0,
  "message": "入库单创建成功",
  "data": {
    "inboundId": 50021,
    "orderNo": "RK20260603001",
    "status": "未入库"
  }
}
```

### 7.3 库存报表与风险预警接口

#### 7.3.1 查询最新动态库存水位报表

| 属性 | 值 |
| :--- | :--- |
| **URL** | `/api/stock/report` |
| **Method** | `GET` |

**请求查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `materialCode` | String | 否 | 物料号模糊检索 |
| `alarmStatus` | String | 否 | 内置水位状态过滤：`NORMAL` / `LOW` / `HIGH` |

**返回数据示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "materialCode": "M_PART_001",
      "materialName": "左前大灯总成",
      "stockQty": 45,
      "minStockDays": 3,
      "maxStockDays": 15,
      "ruleEvaluation": "LOW_STOCK",
      "updatedAt": "2026-06-03 15:30:00"
    }
  ]
}
```

### 7.4 AI 预测与报告接口

#### 7.4.1 触发物料库存风险 AI 预测

| 属性 | 值 |
| :--- | :--- |
| **URL** | `/api/ai/predict` |
| **Method** | `POST` |

**请求体参数**：

```json
{
  "materialCode": "M_PART_001"
}
```

**返回数据示例**：

```json
{
  "code": 0,
  "message": "AI智能预测任务已成功在后台线程异步挂载启动",
  "data": {
    "reportId": 998231,
    "predictionStatus": "PENDING"
  }
}
```

#### 7.4.2 获取指定物料的最新 AI 分析报告

| 属性 | 值 |
| :--- | :--- |
| **URL** | `/api/ai/reports/latest` |
| **Method** | `GET` |

**请求查询参数**：

| 参数 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `materialCode` | String | 是 | 目标零件物料号 |

**返回数据示例**：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 998231,
    "materialCode": "M_PART_001",
    "currentStock": 45,
    "riskType": "LOW_STOCK",
    "riskLevel": "HIGH",
    "analysisContent": "该大灯组件当前物理库存仅剩45件。通过导入的下游整车排产计划推演发现，未来3天内总装车消耗总计需要60件。现有库存无法覆盖未来3天消耗，预计将在后天上午发生零组件断供，请立即进行干预补货。",
    "replenishmentSuggestion": "AI推演模型建议：针对供应商[SUP_VWG_09]发起紧急高优先级的采购补货。建议补货数量为200件（即标准工位器具10箱量），可有效拉升库存水位至10天安全线之上，并避免断供。",
    "suggestedQty": 200,
    "predictionStatus": "SUCCESS",
    "confidence": 0.94,
    "createdAt": "2026-06-03 20:00:15"
  }
}
```

---

## 8. 数据库设计

> **设计原则**：基于实训项目要求，统一为单物理数据库，不拆分多库，表结构务实高效。所有主业务表均包含四个审计基础列：`created_by`、`updated_by`、`created_at`、`updated_at`。

### 8.1 用户表：`users`

存储登录及密码哈希数据。

```sql
CREATE TABLE `users` (
  `user_id`    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `username`   VARCHAR(100) NOT NULL UNIQUE COMMENT '登录账号',
  `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密散列密码',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';
```

### 8.2 物料信息表：`materials`

存储基础物料主数据。

```sql
CREATE TABLE `materials` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
  `material_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '物料号/零件号',
  `material_name` VARCHAR(255) NOT NULL COMMENT '物料名称',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '配套供应商代码',
  `created_by`    VARCHAR(100) DEFAULT 'system',
  `updated_by`    VARCHAR(100) DEFAULT 'system',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料基础档案表';
```

### 8.3 器具表：`appliances`

存储工位器具与标准包装容量关系。

```sql
CREATE TABLE `appliances` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '关联物料号',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '关联供应商',
  `pack_type`     VARCHAR(100) NOT NULL COMMENT '包装器具型号(如小铁箱/塑料周转箱)',
  `pack_capacity` INT NOT NULL COMMENT '标准包装满载容量数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='器具包装参数表';
```

### 8.4 供应商信息表：`suppliers`

```sql
CREATE TABLE `suppliers` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `supplier_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '供应商唯一代码',
  `supplier_name` VARCHAR(255) NOT NULL COMMENT '供应商企业名称',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商名录表';
```

### 8.5 库存记录表：`inventories`

存放实时库存物理余额与设定的控制天数。

```sql
CREATE TABLE `inventories` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code`  VARCHAR(100) NOT NULL UNIQUE COMMENT '物料号',
  `stock_qty`      INT NOT NULL DEFAULT 0 COMMENT '当前仓库实物库存现存量',
  `min_stock_days` INT NOT NULL DEFAULT 3 COMMENT '安全低储控制天数',
  `max_stock_days` INT NOT NULL DEFAULT 15 COMMENT '安全高储积压控制天数',
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物理实际库存记录表';
```

### 8.6 入库单及明细表

#### 8.6.1 入库主表：`inbound_orders`

```sql
CREATE TABLE `inbound_orders` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`      VARCHAR(100) NOT NULL UNIQUE COMMENT '手工入库单号(全局唯一)',
  `status`        VARCHAR(50) NOT NULL DEFAULT '未入库' COMMENT '入库状态: 未入库 / 已完成',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '对应发货供应商',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工入库订单主表';
```

#### 8.6.2 入库单明细表：`inbound_details`

```sql
CREATE TABLE `inbound_details` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细主键',
  `inbound_id`    BIGINT NOT NULL COMMENT '关联主表自增ID',
  `order_no`      VARCHAR(100) NOT NULL COMMENT '关联业务单号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '入库零件号',
  `pack_capacity` INT NOT NULL COMMENT '包装容量快照',
  `plan_qty`      INT NOT NULL COMMENT '计划入库总数',
  `actual_qty`    INT NOT NULL DEFAULT 0 COMMENT '到货现场手工确认实际核销数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单行项目明细表';
```

### 8.7 出库单及明细表

#### 8.7.1 出库主表：`outbound_orders`

```sql
CREATE TABLE `outbound_orders` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`   VARCHAR(100) NOT NULL UNIQUE COMMENT '出库业务单号',
  `status`     VARCHAR(50) NOT NULL DEFAULT '未出库' COMMENT '出库单状态: 未出库 / 已完成',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料出库单主表';
```

#### 8.7.2 出库单明细表：`outbound_details`

```sql
CREATE TABLE `outbound_details` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
  `outbound_id`   BIGINT NOT NULL COMMENT '主表ID',
  `order_no`      VARCHAR(100) NOT NULL COMMENT '主单号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '零件号',
  `pack_capacity` INT NOT NULL COMMENT '出库单器具容量快照',
  `plan_qty`      INT NOT NULL COMMENT '计划领料出库数量',
  `actual_qty`    INT NOT NULL DEFAULT 0 COMMENT '仓库实际下架手工清点确认数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单行项目明细表';
```

### 8.8 二维码信息表：`barcodes`

支持二维码辅助流转。

```sql
CREATE TABLE `barcodes` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '零件编码',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '生产供应商',
  `barcode`       VARCHAR(150) NOT NULL UNIQUE COMMENT '唯一箱单标签看板号',
  `status`        VARCHAR(50) NOT NULL DEFAULT '待入库' COMMENT '二维码生命周期: 待入库 / 在库 / 已出库',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料器具二维码追踪表';
```

### 8.9 AI 库存预测报告表：`ai_inventory_reports`

保存大模型解析回写或 Mock 降级的智能预测长文本报告。

```sql
CREATE TABLE `ai_inventory_reports` (
  `id`                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键报告ID',
  `material_code`           VARCHAR(100) NOT NULL COMMENT '预测目标物料号',
  `current_stock`           INT NOT NULL COMMENT '预测切片时的物理库存数量快照',
  `risk_type`               VARCHAR(50) NOT NULL COMMENT '大模型研判类型: NORMAL / LOW_STOCK / DEAD_STOCK / BOTH',
  `risk_level`              VARCHAR(50) NOT NULL COMMENT '风险等级: LOW / MEDIUM / HIGH / CRITICAL',
  `analysis_content`        TEXT NOT NULL COMMENT 'AI生成的核心库存演进与根因剖析大段文字',
  `replenishment_suggestion` TEXT NOT NULL COMMENT 'AI给出的物料精益补货控制行动计划描述',
  `suggested_qty`           INT DEFAULT 0 COMMENT 'AI给出的量化推荐补货量(件)',
  `prediction_status`       VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '异步诊断进度: PENDING / RUNNING / SUCCESS / MOCKED',
  `confidence`              FLOAT DEFAULT 1.0 COMMENT '模型输出可信度得分',
  `created_at`              DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI库存推演与智能决策报告表';
```

### 8.10 数据库表汇总

| 序号 | 表名 | 说明 |
| :--- | :--- | :--- |
| 1 | `users` | 系统用户表 |
| 2 | `materials` | 物料基础档案表 |
| 3 | `appliances` | 器具包装参数表 |
| 4 | `suppliers` | 供应商名录表 |
| 5 | `inventories` | 物理实际库存记录表 |
| 6 | `inbound_orders` | 手工入库订单主表 |
| 7 | `inbound_details` | 入库单行项目明细表 |
| 8 | `outbound_orders` | 物料出库单主表 |
| 9 | `outbound_details` | 出库单行项目明细表 |
| 10 | `barcodes` | 物料器具二维码追踪表 |
| 11 | `ai_inventory_reports` | AI 库存推演与智能决策报告表 |

---

## 9. 异常处理

### 9.1 业务流转异常处理

| 业务异常场景 | 触发系统表现 | 后端业务对策与返回状态 |
| :--- | :--- | :--- |
| 手工出库数超出物理现存上限 | 作业员清点确认时录入实际数 > 当前 `stock_qty`。 | 拒绝保存，引发事务回滚。抛出状态码 `3001`，前端通知"实物库存扣减产生负数，请核实该物料现场实际在库量"。 |
| 基础档案删除违规 | 物料表已被入库单明细引用，操作员尝试直接在基础页面进行"硬删除"。 | 触发数据库外键约束限制或后端统一校验引用逻辑。拒绝删除，返回 `400`，提示"无法删除：该物料已在入出库单据中使用"。 |

### 9.2 AI 接口异常与超时处理（大模型超时降级）

**触发条件**：API 连接中断 / 凭据失效 / 超时。若大模型网络延迟异常或 API 发生网络波动，由于后端接口设置了 10 秒超时，超过阈值后触发 `java.util.concurrent.TimeoutException`。

**业务保障机制（降级 Mock）**：系统的 `RuleMockEngine` 块截获该报错，将 `prediction_status` 修改为 `MOCKED`。根据本地库存与需求流对比的规则（参见 3.3 节计算公式）自动生成兜底内容：

| 字段 | 降级回填值 |
| :--- | :--- |
| `riskType` | `"LOW_STOCK"` |
| `analysisContent` | `"[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。当前库存已跌破低储天数标准线，预测未来需求存在供应黑洞，产生基础断供风险。"` |
| `suggestedQty` | 安全低储对应的缺口补足件数 |

**效果**：前台看板页面能够依然顺畅渲染并支持一键转单，项目整体流程不卡死。

---

## 10. 关键日志 / 事件

为支持全链路开发追踪，Spring Boot 后端需采用统一的 SLF4J 框架打印结构化日志：

| 日志类型 | 级别 | 格式示例 |
| :--- | :--- | :--- |
| **用户行为痕迹** | INFO | `[Auth] 用户账号 wms_planner 成功登录系统，生成JWT，有效载荷顺延120分钟。` |
| **仓储原子动作更新** | INFO | `[StockChange] 物料 M_PART_001 执行手工入库，物理库存由原 45 变更为 245，关联业务单据：RK20260603001。` |
| **AI 任务启动** | INFO | `[AI-Task] 启动物料 M_PART_001 的异步推演任务，生成报告单占位ID: 998231` |
| **AI 调用成功** | INFO | `[AI-API-Success] 成功接收LLM结构化JSON，耗时2450ms，模型判定风险类型为 LOW_STOCK` |
| **AI 调用超时兜底** | WARN | `[AI-API-Timeout] 调用外部LLM接口超时异常，系统无缝启动精益Mock规则引擎拼装基础降级报告方案` |

> **要求**：入出库服务核销确认、库存触发变更时，强制在事务提交前打印库存变更日志。

---

## 11. 安全细节

| 安全措施 | 说明 |
| :--- | :--- |
| **密码存储安全** | 禁止数据库明文存储密码。采用标准的 `BCryptPasswordEncoder` 进行不可逆散列加密。即便数据库发生泄漏，攻击者也无法通过反向彩虹表破解原始密码。 |
| **会话与传输层鉴权** | 不开启传统的 Session 会话保持机制，全站前后端遵循**无状态（Stateless）**设计。JWT 载荷中不包含密码等敏感信息，设置过期时间为固定的 **7200 秒（2 小时）**。 |
| **敏感环境变量隔离** | 大语言模型 API 的专属 `API_KEY`、`BASE_URL` 以及项目的数据库高危连接凭据，严禁直接明文硬编码在 Spring Boot 的 `application.yml` 或前端 JS 文件中，统一配置在后端的系统环境变量或外部部署脚本中。 |

---

## 12. 技术栈

### 12.1 后端技术栈

| 技术 | 版本 / 说明 |
| :--- | :--- |
| **核心框架** | Spring Boot 3.2.x（提供依赖注入、单体应用基础架构、AOP 核心拦截） |
| **持久层框架** | MyBatis-Plus 3.5.x（免写基础通用 SQL，提供内置分页插件与强类型条件构造器） |
| **数据库驱动与连接池** | MySQL Connector/J 8.x + HikariCP 连接池（高性能、务实稳定） |
| **安全加密工具** | BCrypt（用于账号密码哈希）、JJWT（JWT 令牌快速生成与解析） |

### 12.2 前端技术栈

| 技术 | 版本 / 说明 |
| :--- | :--- |
| **构建与基座** | Vite 5.x + Vue 3（Composition API，`<script setup>` 现代高效语法） |
| **UI 组件库** | Element Plus 2.x（企业级标准中后台基础组件库，深度依赖 Table、Form、Dialog、Card） |
| **网络请求通信** | Axios（支持请求与响应拦截器，统一处理 Token 注入与 401 统一重定向） |
| **前端路由守卫** | Vue Router 4.x（控制非登录状态下无 Token 强制弹回登录页） |

---

## 13. 部署说明

项目支持基于轻量化的 Docker Compose 环境完成单机一键全栈编排。

```yaml
version: '3.8'
services:
  # 1. 智库WMS核心关系型数据库
  wms-mysql:
    image: mysql:8.0
    container_name: wms-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: "WmsRootPassword2026"
      MYSQL_DATABASE: "smart_wms_db"
    volumes:
      - ./mysql_data:/var/lib/mysql
      - ./init_script.sql:/docker-entrypoint-initdb.d/init_script.sql  # 自动初始化11张表和初始用户

  # 2. 后端单体应用服务
  wms-backend:
    image: openjdk:17-jdk-slim
    container_name: wms-backend
    ports:
      - "8080:8080"
    depends_on:
      - wms-mysql
    environment:
      SPRING_DATASOURCE_URL: "jdbc:mysql://wms-mysql:3306/smart_wms_db?useSSL=false&serverTimezone=GMT%2B8"
      SPRING_DATASOURCE_USERNAME: "root"
      SPRING_DATASOURCE_PASSWORD: "WmsRootPassword2026"
      AI_LLM_API_KEY: "sk-your-real-llm-api-access-token-string"       # 动态注入外部大模型秘钥
      AI_LLM_BASE_URL: "https://api.llm-provider.com/v1"
    volumes:
      - ./target/smart-wms-backend-1.0.jar:/app/wms-backend.jar
    command: ["java", "-jar", "/app/wms-backend.jar"]

  # 3. 前端编译产物静态托管容器
  wms-frontend:
    image: nginx:alpine
    container_name: wms-frontend
    ports:
      - "80:80"
    volumes:
      - ./dist:/usr/share/nginx/html
      - ./nginx.conf:/etc/nginx/nginx.conf  # 配置反向代理，解决前端Axios跨域访问8080问题
    depends_on:
      - wms-backend
```

---

## 14. 演示场景

### 14.1 手工入库作业与物理报表动态刷新

1. 操作员登录系统，进入【入出库作业】 → 点击"创建入库单"。
2. 供应商下拉框选"一汽大众佛山配件厂"，添加一行零件：输入物料号 `M_PART_001`（左前大灯），单包装容量为 20 件/箱，计划入库数填入 200。点击保存。
3. 此时可在入库列表看到该单据，状态标为"未入库"。点击行尾的"手工到货核销"。
4. 弹窗中输入实际到货清点确认数：200。点击确认。
5. **系统表现**：单据状态瞬间改变为"已完成"。此时切入到【库存报表页】，可以看到物料 `M_PART_001` 的物理现存量从原本的 0 变成了 200 件，高低储评级恢复为 `NORMAL`（正常），演示成功。

### 14.2 物料超低储断供触发 AI 智能预测

1. 延续上例，在出库管理中导入下游整车生产计划预测流：显示未来一周内对 `M_PART_001` 的消耗总额达到 350 件，而当前实际库存仅 200 件。
2. 刷新【库存预警页】，内置规则逻辑通过动态计算得出当前库存天数小于预设的安全天数，该行整行高亮显示浅红，标识"超低储违规"。
3. 操作员点击行尾的"触发 AI 深度推演"按钮。页面展示局部 Loading 加载。
4. **系统表现**：后端生成 `PENDING` 状态的异步报告，工作线程池接管并向外部大模型 API 发起标准 Prompt 投递。

### 14.3 AI 报告回写与一键转化闭环决策

1. 3 秒后，AI 推演线程顺利捕获大语言模型反馈的精准 JSON。
2. 前端 Loading 结束，变为绿色的"分析完成"标签。操作员点击打开该行的"智能 AI 报告详页"。
3. **系统表现**：页面完美渲染出大模型的智能推演文字："低储断供高风险！现有库存将在第 4 天完全耗尽，导致总装二线停产…"。同时在建议区明确写出："建议向供应商加单补货 160 件（即标准器具 8 箱）"。
4. 操作员由于看到了明确的量化采购依据，直接点击报告卡片右上角的 **"采纳建议：一键转标准入库单"** 按钮。
5. **系统表现**：页面丝滑跳转到新建入库单表单，且物料号 `M_PART_001`、供应商代码、建议计划数量 160 均已由 AI 数据上下文自动妥善预填，用户只需核对后点击保存即可。

---

## 15. 验收标准

### 15.1 登录与鉴权验收

- [ ] 输入预置的账号密码能成功换取合法 JWT。
- [ ] 若密码错误或账号不存在，接口必须准确拦截并返回对应错误提示，不允许直接发生后端崩溃。
- [ ] 浏览器 `localStorage` 抹除 Token 后，尝试手工更改浏览器地址进入库存页面，Vue 路由守卫必须予以重定向拦截并抛回至登录初始页。

### 15.2 基础信息与仓储流转验收

- [ ] 物料表、器具表、供应商表等 3 张基础信息表全面实现基础增删改查。
- [ ] 执行手工入库确认与实际出库确认操作时，`inventories` 表中的 `stock_qty` 实物库存数量必须产生准确、即时的数字原子性加减，绝不能出现账实不符。
- [ ] 当出库确认数大于实物库存量时，后端必须成功抛出 `3001` 业务错误码予以阻断，杜绝产生负库存脏数据。

### 15.3 AI 异步预测与流程不卡死验收

- [ ] 点击"触发 AI 风险预测"时，前台界面不允许发生任何假死、白屏或整页卡住。
- [ ] 当大模型因未知的外部因素（断网、Token 额度超限等）中断返回或严重延迟时，后端必须能够在 10 秒内触发超时并切换到 `RuleMockEngine` 本地降级通道。此时报告状态必须记为 `MOCKED` 状态，页面上能够正常看到本地降级后的基础库存建议文本，核心闭环流转不崩溃。

### 15.4 接口返回与页面视觉验收

- [ ] 使用浏览器的 F12 开发者工具抓包，观察所有控制器响应，其最外层必须完全契合 `{code, message, data}` 的三段统一泛型包装对象。
- [ ] 库存看板和报表页中，针对超低储物料、超高储滞销物料必须拥有区别于常规物料行的鲜明色彩警示（如浅红/浅黄高亮或专属警告标签）。
- [ ] AI 报告详页的各个结构化卡片（风险研判、根因剖析、补货建议数量）必须要能与大模型传回的 JSON 键值对发生准确无误的绑定呈现，且"一键转入库单"按钮在低储高风险状态下能完全正常工作。

---

## 16. 注释规范

> 本章节定义了智库WMS项目的代码注释标准，目标是保证代码的可读性与可维护性，使注释真正服务于团队协作与知识传承。

### 16.1 语言要求

所有代码注释（单行注释、多行注释、文档字符串）**统一使用简体中文**。

```java
// ✅ 正确：中文注释
// 校验出库数量是否超过物理库存上限

// ❌ 错误：英文注释
// Check if outbound qty exceeds physical stock
```

### 16.2 注释内容原则

注释应当解释 **"为什么这么做"**，而不是 **"做了什么"**。代码本身应当自解释实现细节，注释用于说明以下非显而易见的内容：

| 需要注释的场景 | 示例 |
| :--- | :--- |
| **业务逻辑背景** | `// 先进先出（FIFO）核销：按二维码创建时间升序扣减，确保老批次优先出库` |
| **复杂算法说明** | `// 使用加权移动平均法计算日均消耗，权重随天数递减以突出近期趋势` |
| **临时解决方案** | `// TODO(开发者, 2026-06-15): 当前为桩实现，后续对接真实大模型 API 替换` |
| **性能考量** | `// 批量插入二维码记录，避免逐条 insert 导致 200+ 次数据库往返` |
| **非显而易见的设计决策** | `// 使用 CallerRunsPolicy 而非 AbortPolicy：AI 预测非核心链路，拒绝时在调用者线程执行降级，保证主业务不受阻` |

**禁止添加无意义的注释**，例如：

```java
// ❌ 禁止
i = i + 1;        // 将 i 加 1
// ❌ 禁止
// 开始循环
for (Item item : list) {
// ❌ 禁止
return price;     // 返回 price
```

简单 getter/setter、显而易见的条件判断**无需注释**。

### 16.3 类与模块文档注释

每个公共类、接口、模块应包含文档字符串，说明其职责与定位。格式遵循各语言惯例：

**Java 类（Javadoc 风格）**：

```java
/**
 * 入库单服务实现，负责入库单的创建、核销确认与库存原子性更新。
 * 所有写操作使用 @Transactional 保证事务一致性。
 *
 * @author <开发者姓名>
 * @date 2026-06-03
 */
@Service
public class InboundServiceImpl implements InboundService {
```

**Vue 组件（HTML 注释风格）**：

```vue
<!--
  智能库存看板首页 — 统计概览 + 库存水位表 + AI 速查。
  @author <开发者姓名>
  @date 2026-06-03
-->
<template>
```

**JavaScript 模块（JSDoc 风格）**：

```javascript
/**
 * Axios 请求封装 — 统一 Token 注入与 401 拦截。
 *
 * @author <开发者姓名>
 * @date 2026-06-03
 */
```

### 16.4 函数与方法文档注释

每个公共函数/方法应包含文档字符串，说明用途、参数、返回值及可能抛出的异常。格式遵循语言惯例：

**Java 方法**：

```java
/**
 * 手工确认入库，核销明细数量并原子性更新库存。
 * 所有操作在同一事务中执行，任何一步失败全面回滚。
 *
 * @param inboundId 入库单主键 ID
 * @throws BusinessException 当入库单已完成核销时抛出（code=400）
 * @throws BusinessException 当入库单不存在时抛出（code=404）
 */
@Transactional(rollbackFor = Exception.class)
public void confirm(Long inboundId) {
```

**JavaScript 函数**：

```javascript
/**
 * 触发物料 AI 风险预测（异步）。
 * 调用后立即返回 PENDING 状态，实际推演在后台线程池执行。
 *
 * @param {string} materialCode 物料号
 * @returns {Promise<{reportId: number, predictionStatus: string}>}
 */
export function triggerPredict(materialCode) {
```

### 16.5 特殊标记规范

#### @author 和 @date 标签

公共类、模块、函数应包含 `@author`（填写实际开发者姓名）和 `@date`（格式 `YYYY-MM-DD`）标签，用于追溯代码归属与变更时间线。

```java
/**
 * @author <开发者姓名>
 * @date 2026-06-03
 */
```

#### @param / @return / @throws 标签

用于说明参数、返回值、异常信息。格式遵循语言惯例：

- **Java**: `@param`, `@return`, `@throws`
- **JavaScript / TypeScript**: `@param`, `@returns`, `@throws`
- **Vue Template**: 在组件顶部的 HTML 注释中简述

#### @see 标签

引用相关函数或文档链接，用于标注关联代码的定位线索：

```java
/**
 * @see com.smartwms.engine.RuleMockEngine#generateMockReport 降级 Mock 引擎
 * @see <a href="https://platform.openai.com/docs">OpenAI API 文档</a>
 */
```

### 16.6 TODO 和 FIXME 规范

所有临时标记必须包含**负责人**和**日期**，格式如下：

```java
// TODO(开发者, 2026-06-15): 后续对接真实大模型 API，替换当前桩实现
// FIXME(开发者, 2026-06-03): 当前 H2 数据库不支持 MERGE 语句的 MySQL 兼容模式，需切换为 INSERT ... ON DUPLICATE KEY
```

**严禁**留下无归属、无日期的 TODO/FIXME：

```java
// ❌ 禁止
// TODO: fix this later
// ❌ 禁止  
// FIXME: 这里有问题
```

### 16.7 注释维护要求

| 规则 | 说明 |
| :--- | :--- |
| **同步更新** | 修改代码时，必须同步更新受影响的注释。注释与代码实际行为不一致视为需要立即修复的问题。 |
| **废弃代码** | 禁止在被注释掉的代码块旁不加说明；如需保留被注释的代码，必须注释说明保留原因及预期删除时间。 |
| **注释密度** | 鼓励在复杂业务逻辑、正则表达式、算法边界条件、外部 API 调用等处添加注释。 |

### 16.8 禁止的注释行为

| 禁止行为 | 说明 |
| :--- | :--- |
| ❌ **抱怨式注释** | 不要在注释中抱怨历史代码或批评他人（例如"这段代码很烂"、"不知道谁写的垃圾逻辑"）。 |
| ❌ **ASCII 分隔线** | 不要在注释中使用无意义的 ASCII 艺术或分隔线（如 `// ==========` 和 `// --------`）。 |
| ❌ **敏感信息泄露** | 不要在注释中留下密码、密钥、Token、个人隐私等敏感信息。 |
| ❌ **无效署名** | 不要在注释中添加与项目无关的个人标识或网络昵称。 |

### 16.9 注释示例汇总

#### Java 完整示例

```java
/**
 * 库存扣减原子操作。
 * 采用数据库行级锁保证并发安全，避免超卖。
 *
 * @author <开发者姓名>
 * @date 2026-06-03
 * @param materialCode 物料号
 * @param qty          扣减数量
 * @throws BusinessException 当库存不足时抛出（code=3001）
 * @see com.smartwms.service.StockService#getStockReport 库存报表查询
 */
@Transactional(rollbackFor = Exception.class)
public void deductStock(String materialCode, int qty) {
    // 使用 SELECT ... FOR UPDATE 对库存行加悲观锁，防止并发扣减导致负库存
    Inventory inv = inventoryMapper.selectForUpdate(materialCode);
    if (inv.getStockQty() < qty) {
        // 库存不足，回滚事务并返回业务错误码
        throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "实物库存余额不足");
    }
    inv.setStockQty(inv.getStockQty() - qty);
    inventoryMapper.updateById(inv);
}
```

#### Vue 组件完整示例

```vue
<!--
  用户登录页 — 简洁纯色风格，左侧品牌区 + 右侧表单区。
  @author <开发者姓名>
  @date 2026-06-03
-->
<template>
  <div class="login-wrapper">
    <!-- 表单区域 -->
  </div>
</template>

<script setup>
/**
 * 用户登录页逻辑。
 * 登录成功后通过 Pinia 存储 Token 并跳转至看板。
 */
import { ref, reactive } from 'vue'

/**
 * 执行登录请求。
 * 校验通过后写入 localStorage 并跳转。
 */
async function handleLogin() {
  // 先做前端表单校验，减少无效请求
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // ...后续逻辑
}
</script>
```

#### SQL 注释完整示例

```sql
-- 智库WMS - 数据库初始化脚本（11 张表 + 预置初始数据）
-- @author <开发者姓名>
-- @date 2026-06-03

CREATE TABLE `inbound_orders` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`   VARCHAR(100) NOT NULL UNIQUE COMMENT '手工入库单号（全局唯一，格式 RK+日期+序号）',
  `status`     VARCHAR(50)  NOT NULL DEFAULT '未入库' COMMENT '入库状态：未入库 / 已完成',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工入库订单主表';
```

### 16.10 审查检查清单

代码审查时应对注释质量进行以下检查：

- [ ] 新增公共类/函数是否包含 `@author` 和 `@date` 标签
- [ ] 复杂业务逻辑是否有解释"为什么"的注释
- [ ] 是否存在无意义的注释（如"i = i + 1 // i 加 1"）
- [ ] TODO/FIXME 是否包含负责人和日期
- [ ] 被注释掉的代码是否有保留原因说明
- [ ] 是否存在抱怨式注释或 ASCII 艺术
- [ ] 注释中是否包含敏感信息（密码、密钥等）
- [ ] 修改代码后是否同步更新了受影响的注释
