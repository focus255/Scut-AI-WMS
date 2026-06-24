# 智库WMS — 智能仓储管理系统

基于 **Spring Boot 3.2 + Vue 3 + Element Plus + MySQL 8.0** 的智能仓储管理系统，融合大语言模型（LLM）实现库存风险智能预测与补货建议自动生成。

## 项目结构

```
├── README.md
├── wms_PRD.md                       # 产品需求文档
├── 库存报表与风险预警参数说明.md       # 评级体系与计算公式详解
├── .gitignore
│
├── backend/                         # Spring Boot 3.2 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/smartwms/
│       │   ├── SmartWmsApplication.java
│       │   ├── common/              # Result、JwtUtil、ErrorCode、异常处理
│       │   ├── config/              # WebMvc、AI线程池、CORS、BCrypt、DataInitializer
│       │   ├── interceptor/         # JWT 鉴权拦截器
│       │   ├── controller/          # Auth、Material、Inbound、Outbound、Stock 等
│       │   ├── service/             # 业务接口 + impl 实现
│       │   ├── mapper/              # MyBatis-Plus Mapper（17 张表）
│       │   ├── entity/              # 数据库实体
│       │   ├── dto/                 # 请求/响应 DTO
│       │   └── engine/              # RuleMockEngine AI 降级规则引擎
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml   # 开发环境 MySQL
│           ├── application-prod.yml  # 生产环境 MySQL
│           └── db/
│               ├── init_script.sql   # DDL 建表脚本
│               └── seed_data.sql     # 种子数据脚本（唯一数据源）
│
└── frontend/                        # Vue 3 + Vite + Element Plus 前端
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.js
        ├── App.vue
        ├── assets/global.css
        ├── api/                     # Axios + 10 个业务模块
        ├── router/index.js          # 路由 + 登录守卫
        ├── stores/user.js           # Pinia 用户状态
        ├── components/              # AppLayout、ChartCard、BarcodeScanner 等
        └── views/                   # 14 个业务页面
```

## 技术栈

| 层级 | 技术 | 版本 |
|:---|:---|:---|
| 后端框架 | Spring Boot | 3.2.6 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL 8.0 (Docker / 原生) | — |
| 安全 | BCrypt + JJWT | 0.12.6 |
| 前端 | Vue 3 Composition API | 3.4.x |
| UI | Element Plus | 2.14.x |
| 构建 | Vite | 5.x |
| 图表 | ECharts | 6.1.0 |
| 扫码 | html5-qrcode | 2.3.8 |

## 快速开始

### 环境要求

- **JDK** 17+
- **Maven** 3.8+
- **Node.js** 18+
- **MySQL** 8.0+ 或 Docker

### 第一步：启动 MySQL

```bash
# Docker 方式（推荐）
docker run -d --name wms-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=smart_wms_dev \
  -p 3306:3306 \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

### 第二步：导入数据

```bash
# 建表
docker exec -i wms-mysql mysql -uroot -proot --default-character-set=utf8mb4 smart_wms_dev \
  < backend/src/main/resources/db/init_script.sql

# 种子数据（一次性，含 45 张入库单、467 条出库历史、700+ 二维码）
docker exec -i wms-mysql mysql -uroot -proot --default-character-set=utf8mb4 smart_wms_dev \
  < backend/src/main/resources/db/seed_data.sql
```

> 如使用原生 MySQL，将 `docker exec -i wms-mysql` 替换为标准 `mysql` 客户端命令。

### 第三步：启动后端

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8080
```

### 第四步：启动前端

```bash
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

### 测试账号

| 账号 | 密码 | 角色 |
|:---|:---|:---|
| `admin` | `admin123` | 管理员 |
| `operator` | `operator123` | 操作员 |

## 功能清单

| 模块 | 功能 | 状态 |
|:---|:---|:--|
| **用户认证** | 登录 / 注册 / JWT 鉴权 / RBAC 权限 | ✅ |
| **物料管理** | 物料 CRUD / 供应商管理 / 器具配置 | ✅ |
| **入库管理** | 入库单创建 / 确认入库 / 二维码自动生成 / 扫码入库 | ✅ |
| **出库管理** | 出库单创建 / FIFO 拣选 / 一码到底追溯 | ✅ |
| **库存报表** | 四级评级（呆滞/低储/高储/正常）/ 多维筛选 / CSV导出 | ✅ |
| **看板** | 统计概览 / 库存水位饼图 / Top10柱状图 / 实时告警轮询 | ✅ |
| **AI 预测** | 异步触发 / RuleMockEngine 降级 / 补货建议 | ✅ |
| **封存管理** | 二维码封存 / 解封 / 质量/管理分类 | ✅ |
| **扫码** | 独立扫码页面 / 入库核销 / 出库拣选 | ✅ |
| **PWA** | 移动端离线访问 | ✅ |

## 库存评级体系

系统对每种物料进行四级库存评级，优先级如下：

| 优先级 | 评级 | 判定规则 |
|:---:|:---|:---|
| 1 | **呆滞** (DEAD_STOCK) | 最后出库距今 ≥ 90 天，且库存 > 0 |
| 2 | **低储** (LOW_STOCK) | 库存 < (日均销量 × 补货提前期) + 安全库存 |
| 3 | **高储** (HIGH) | DOHF = 库存 ÷ 日均销量 > 高储控制天数上限 |
| 4 | **正常** (NORMAL) | 以上条件均不满足 |

> 详见 [库存报表与风险预警参数说明](./库存报表与风险预警参数说明.md)

## 种子数据规模

| 实体 | 数量 | 说明 |
|:---|:---|:---|
| 入库单 | 45 | 每种物料 3 批历史入库（150～15天前） |
| 二维码 | ~700 | 每箱独立二维码，格式 `WMS|物料|供应商|…` |
| 出库单 | 467 | 120天跨度，按 FIFO 从真实二维码拣选 |
| 出库流水 | 959 | 含跨箱拆分的完整追溯链路 |
| AI 报告 | 8 | SUCCESS/MOCKED/PENDING 全覆盖 |
| 封存记录 | 4 | 质量封存 / 管理封存 / 已解封 |

## API 概览

所有响应统一格式 `{ "code": 0, "message": "success", "data": {...} }`

| 方法 | 端点 | 说明 |
|:---|:---|:---|
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/materials` | 物料分页查询 |
| GET/POST/PUT/DELETE | `/api/inbound/orders` | 入库单 CRUD |
| GET/POST/PUT | `/api/outbound/orders` | 出库单 CRUD |
| GET | `/api/stock/report` | 库存报表（四级评级） |
| POST | `/api/ai/predict` | 触发 AI 预测 |
| GET | `/api/ai/reports/latest` | 最新 AI 报告 |
| GET/POST | `/api/freeze` | 封存/解封管理 |
| GET | `/api/inbound/trace` | 库存追溯 |

> 完整 API 文档见 [wms_PRD.md](./wms_PRD.md)

## 数据库表（17 张）

| 类别 | 表名 |
|:---|:---|
| 认证权限 | `users` `roles` `permissions` `user_roles` `role_permissions` |
| 基础数据 | `materials` `appliances` `suppliers` |
| 入库业务 | `inbound_orders` `inbound_details` |
| 出库业务 | `outbound_orders` `outbound_details` `outbound_histories` |
| 库存追踪 | `inventories` `barcodes` `inventory_freezes` |
| AI 分析 | `ai_inventory_reports` |

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

## License

Internal Use Only — 智库WMS 内部项目
