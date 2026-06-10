# 智库WMS — 智能仓储管理系统

基于 **Spring Boot 3.2 + Vue 3 + Element Plus + MySQL** 的智能仓储管理系统，在传统 WMS 仓储流转业务之上融合大语言模型（LLM），实现库存风险智能预测与补货建议自动生成。

## 项目结构

```
├── wms_PRD.md                     # 产品需求文档
├── README.md                      # 本文件
├── .gitignore
│
├── backend/                       # Spring Boot 3.2 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/smartwms/
│       │   ├── SmartWmsApplication.java
│       │   ├── common/            # Result 统一响应、JwtUtil、ErrorCode、异常处理
│       │   ├── config/            # WebMvc、AI线程池、MyBatis-Plus、CORS、BCrypt、数据初始化
│       │   ├── interceptor/       # JwtInterceptor JWT 鉴权拦截器
│       │   ├── controller/        # Auth、Material、Inbound、Stock、AiReport、Supplier 等
│       │   ├── service/           # 业务接口 + impl 实现
│       │   ├── mapper/            # MyBatis-Plus Mapper（15 张表）
│       │   ├── entity/            # 数据库实体
│       │   ├── dto/               # 请求/响应 DTO
│       │   └── engine/            # RuleMockEngine AI 降级规则引擎
│       └── resources/
│           ├── application.yml    # 公共配置（默认激活 dev profile）
│           ├── application-dev.yml # 开发环境：H2 内存数据库
│           ├── application-prod.yml # 生产环境：MySQL 持久化
│           └── db/init_script.sql # 15 张表 DDL（H2/MySQL 兼容）
│
└── frontend/                      # Vue 3 + Vite + Element Plus 前端
    ├── package.json
    ├── vite.config.js             # 构建配置 + API 代理 + Element Plus 按需导入
    ├── index.html
    └── src/
        ├── main.js                # 入口（CSS 加载顺序确保弹窗样式优先）
        ├── App.vue
        ├── assets/global.css      # 全局样式变量 + 表格高亮 + 自动填充修复
        ├── api/                   # Axios 封装 + 5 个业务模块
        │   ├── request.js         # Token 注入、401 拦截、错误透传
        │   ├── auth.js            # 登录/注册
        │   ├── materials.js       # 物料 CRUD
        │   ├── inbound.js         # 入库单
        │   ├── stock.js           # 库存报表
        │   └── ai.js              # AI 预测/报告
        ├── router/index.js        # 路由表 + 登录守卫（noAuth 白名单）
        ├── stores/user.js         # Pinia 用户状态
        ├── components/
        │   └── AppLayout.vue      # 暗色侧边栏 + 顶栏 + 内容区主布局
        └── views/
            ├── Login.vue          # 登录页（品牌双栏风格）
            ├── Register.vue       # 注册页
            ├── Dashboard.vue      # 智能看板（统计+库存+AI速查+扫码入库+打印）
            ├── Materials.vue      # 物料/供应商/器具管理（Tab+CRUD）
            ├── InboundOutbound.vue # 入库单管理（建单/编辑/确认/打印/详情）
            ├── InventoryTrace.vue # 库存追溯查询
            ├── StockReport.vue    # 库存报表（红黄绿水位高亮）
            └── AiReport.vue       # AI 报告详页（一键转入库单）
```

## 技术栈

| 层级 | 技术 | 版本 |
|:---|:---|:---|
| 后端框架 | Spring Boot | 3.2.6 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL 8.0（开发环境 H2） | — |
| 连接池 | HikariCP | — |
| 安全 | BCrypt + JJWT 0.12.6 | — |
| 前端框架 | Vue 3 Composition API (`<script setup>`) | 3.4.x |
| UI 组件库 | Element Plus | 2.14.x |
| 构建工具 | Vite | 5.x |
| 路由 | Vue Router | 4.x |
| 状态管理 | Pinia | 2.x |
| HTTP 客户端 | Axios | 1.7.x |

## 快速开始

### 环境要求

- **JDK** 17+
- **Maven** 3.8+
- **Node.js** 18+
- **MySQL** 8.0+（仅生产环境需要）

---

### 开发环境启动（H2 内存数据库）

> 无需安装 MySQL，后端自动使用 H2 内存数据库并装载种子数据。
> 默认激活 `dev` profile。

**启动后端**

```bash
cd backend
mvn spring-boot:run
# 启动于 http://localhost:8080
# H2 控制台: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:smart_wms_db
```

**启动前端**

```bash
cd frontend
npm install
npm run dev
# 启动于 http://localhost:5173
# API 请求自动代理到 localhost:8080
```

---

### 生产环境部署（MySQL 持久化）

> 激活 `prod` profile，数据库连接信息通过环境变量注入。

**1. 环境变量**

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:mysql://your-host:3306/smart_wms_db?useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME="your_user"
export SPRING_DATASOURCE_PASSWORD="your_password"
export JWT_SECRET="your-256-bit-production-secret-key-change-this!!"
export SERVER_PORT=8080
```

**2. 创建数据库**

```sql
CREATE DATABASE smart_wms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**3. 执行建表脚本（仅首次部署）**

```bash
mysql -u your_user -p smart_wms_db < backend/src/main/resources/db/init_script.sql
```

**4. 创建初始管理员（仅首次部署）**

> 使用 BCrypt 在线工具生成密码散列，或通过应用注册接口创建首个用户。

```sql
-- 示例：插入 admin 用户（密码散列需通过 BCrypt 生成）
INSERT INTO users (username, password, nickname, status)
VALUES ('admin', '<BCRYPT_HASH>', '管理员', 'ENABLED');
```

**5. 编译并启动**

```bash
cd backend
mvn package -DskipTests
java -jar target/smart-wms-backend-1.0.0.jar --spring.profiles.active=prod
```

**6. 前端部署**

```bash
cd frontend
npm install
npm run build
# 将 dist/ 目录部署到 Nginx / CDN
# Nginx 配置示例：将 /api 反向代理到后端 8080 端口
```

---

### 测试账号

| 账号 | 密码 | 角色 | 环境 |
|:---|:---|:---|:---|
| `admin` | `admin123` | 管理员（仓储主管） | 开发环境自动创建 |
| `operator` | `operator123` | 操作员（仓库作业员） | 开发环境自动创建 |

> 生产环境不自动创建测试账号，需通过注册页或数据库手动创建。

---

## 功能清单

### 已实现

| 模块 | 功能 | 前端 | 后端 |
|:---|:---|:--|:--|
| **用户认证** | 登录 / 注册 / JWT 鉴权 / 退出 | ✅ | ✅ |
| **用户认证** | RBAC 权限体系（角色/权限/用户角色管理） | — | ✅ |
| **用户认证** | 路由守卫（未登录强制跳转） | ✅ | — |
| **用户认证** | BCrypt 密码加密 + DataInitializer 预置账号 | — | ✅ |
| **物料管理** | 分页列表 / 新增 / 编辑 / 删除（引用校验） | ✅ | ✅ |
| **供应商管理** | 分页列表 / 新增 / 编辑 / 删除（引用校验） | ✅ | ✅ |
| **器具配置** | 分页列表 / 新增 / 编辑 / 删除 | ✅ | ✅ |
| **入库管理** | 新建入库单 / 修改入库单 / 确认入库 / 条码自动生成 | ✅ | ✅ |
| **入库管理** | 扫码入库（Dashboard 扫码框）+ 按条码核销 | ✅ | ✅ |
| **入库管理** | AI 报告一键预填入库单 | ✅ | ✅ |
| **入库管理** | 打印入库单（预览 + 浏览器打印） | ✅ | — |
| **库存追溯** | 按物料/条码/入库单号查询条码生命周期轨迹 | ✅ | ✅ |
| **库存报表** | 多维列表 / 高低储评级 / 红黄绿行高亮 / 筛选 | ✅ | ✅ |
| **AI 报告** | 6 份种子报告（SUCCESS/MOCKED/PENDING） | ✅ | ✅ |
| **AI 预测** | 异步触发 / 最新报告查询 / RuleMockEngine 降级 | ✅ | ✅ |
| **看板** | SKU 统计 / 呆滞物料数 / 高风险数 / AI 速查 | ✅ | ✅ |
| **看板** | 扫码入库快捷入口 / 打印看板 / 追溯入口 | ✅ | — |
| **环境切换** | dev（H2 内存库）/ prod（MySQL 持久化）Profile | — | ✅ |

### 待开发

| 模块 | 功能 |
|:---|:---|
| 出库管理 | 出库单创建、FIFO 核销、库存扣减 |
| AI 引擎 | 对接真实大模型 API（当前为 Mock 桩） |
| 零件需求导入 | CSV/文本域导入未来需求预测 |
| 图形化图表 | ECharts 库存趋势图、物料周转率等 |
| 操作日志 | 关键业务操作审计日志 |

---

## 种子数据说明

> **仅开发环境（`dev` profile）**：`DataInitializer` 在启动时自动装载种子数据。
> 生产环境（`prod` profile）不自动插入数据。

| 实体 | 数量 | 场景覆盖 |
|:---|:---|:---|
| 用户/角色/权限 | 2 用户 + 3 角色 + 6 权限 | 管理员 + 操作员，RBAC 权限矩阵 |
| 物料 | 15 种 | 分页测试（>10 条）+ 多供应商关联 |
| 供应商 | 5 家 | 下拉选择 + 物料归属 |
| 器具包装 | 15 条 | 4 种包装类型 + 不同容量 |
| 库存 | 14 条 | 🔴 低储 5 / 🟡 高储 6 / 🟢 正常 4 |
| AI 报告 | 6 份 | SUCCESS×4 / MOCKED×1 / PENDING×1 |
| 入库单 | 3 张 | 已完成×2 / 未入库×1 |

---

## API 文档

### 认证

| 方法 | 端点 | 鉴权 | 说明 |
|:---|:---|:---|:---|
| POST | `/api/auth/register` | 无 | 用户注册 |
| POST | `/api/auth/login` | 无 | 用户登录，返回 JWT（有效期 2h） |

### 物料

| 方法 | 端点 | 鉴权 | 说明 |
|:---|:---|:---|:---|
| GET | `/api/materials?page=&size=&keyword=` | JWT | 分页查询 |
| GET | `/api/materials/{id}` | JWT | 按 ID 查询 |
| POST | `/api/materials` | JWT | 新增物料 |
| PUT | `/api/materials/{id}` | JWT | 更新物料 |
| DELETE | `/api/materials/{id}` | JWT | 删除物料（校验引用） |

### 入库

| 方法 | 端点 | 鉴权 | 说明 |
|:---|:---|:---|:---|
| GET | `/api/inbound/orders?page=&size=` | JWT | 入库单分页列表 |
| GET | `/api/inbound/orders/{id}` | JWT | 入库单详情（含明细行） |
| POST | `/api/inbound/orders` | JWT | 新建入库单（自动生成条码） |
| PUT | `/api/inbound/orders/{id}` | JWT | 修改入库单（仅未入库状态） |
| PUT | `/api/inbound/orders/{id}/confirm` | JWT | 确认入库（支持按行传入实际数量） |
| POST | `/api/inbound/scan` | JWT | 扫码入库（按条码核销单箱） |
| GET | `/api/inbound/trace?materialCode=&barcode=&orderNo=` | JWT | 库存追溯查询 |

### 库存报表

| 方法 | 端点 | 鉴权 | 说明 |
|:---|:---|:---|:---|
| GET | `/api/stock/report?materialCode=&alarmStatus=` | JWT | 库存水位报表 |

### AI 预测

| 方法 | 端点 | 鉴权 | 说明 |
|:---|:---|:---|:---|
| POST | `/api/ai/predict` | JWT | 触发异步 AI 预测 |
| GET | `/api/ai/reports/latest?materialCode=` | JWT | 查询最新 AI 报告 |

> 所有响应统一格式：`{ "code": 0, "message": "success", "data": {...} }`  
> 详细说明见 [wms_PRD.md](./wms_PRD.md)

---

## 错误码

| code | 语义 |
|:---|:---|
| 0 | 成功 |
| 400 | 参数校验失败 / 业务拒绝 |
| 401 | 未登录或 Token 失效 |
| 404 | 资源不存在 |
| 500 | 系统内部异常 |
| 2001 | AI 线程池满 |
| 2002 | AI 接口超时（已触发 Mock 降级） |
| 3001 | 库存不足 |

---

## 数据库

共 15 张表：

| 类别 | 表名 | 说明 |
|:---|:---|:---|
| 认证权限 | `users` `roles` `permissions` `user_roles` `role_permissions` | RBAC 权限体系 |
| 基础数据 | `materials` `appliances` `suppliers` | 物料 / 器具 / 供应商 |
| 入库业务 | `inbound_orders` `inbound_details` | 入库单主表 + 明细 |
| 出库业务 | `outbound_orders` `outbound_details` | 出库单主表 + 明细（预留） |
| 库存追踪 | `inventories` `barcodes` | 库存现存量 + 条码生命周期 |
| AI 分析 | `ai_inventory_reports` | LLM 库存推演报告 |

所有主业务表均包含审计字段：`created_by` / `updated_by` / `created_at` / `updated_at`

---

## 开发约定

- **注释语言**：所有注释使用简体中文，解释"为什么"而非"做了什么"
- **敏感文件**：`.env`、密钥等不纳入版本控制

---

## License

Internal Use Only — 智库WMS 内部项目
