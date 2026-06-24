-- ===================================================================
-- 智库WMS - MySQL 数据库初始化 DDL 脚本
-- 种子数据请使用 seed_data.sql 导入
-- @author Focus
-- @date 2026-06-24
-- ===================================================================

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS `users` (
  `user_id`    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `username`   VARCHAR(100) NOT NULL UNIQUE COMMENT '登录账号',
  `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密散列密码',
  `nickname`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '用户昵称',
  `status`     VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '用户状态: ENABLED / DISABLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 角色表
CREATE TABLE IF NOT EXISTS `roles` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `role_code`  VARCHAR(100) NOT NULL UNIQUE COMMENT '角色编码',
  `role_name`  VARCHAR(100) NOT NULL COMMENT '角色名称',
  `remark`     VARCHAR(255) DEFAULT '' COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 3. 权限表
CREATE TABLE IF NOT EXISTS `permissions` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `permission_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
  `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
  `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 4. 用户角色关联表
CREATE TABLE IF NOT EXISTS `user_roles` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `user_id`    BIGINT NOT NULL COMMENT '用户ID',
  `role_id`    BIGINT NOT NULL COMMENT '角色ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (`user_id`, `role_id`)
);

-- 5. 角色权限关联表
CREATE TABLE IF NOT EXISTS `role_permissions` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `role_id`       BIGINT NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT NOT NULL COMMENT '权限ID',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (`role_id`, `permission_id`)
);

-- 6. 物料基础档案表
CREATE TABLE IF NOT EXISTS `materials` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
  `material_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '物料号/零件号',
  `material_name` VARCHAR(255) NOT NULL COMMENT '物料名称',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '配套供应商代码',
  `created_by`    VARCHAR(100) DEFAULT 'system',
  `updated_by`    VARCHAR(100) DEFAULT 'system',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 7. 器具包装参数表
CREATE TABLE IF NOT EXISTS `appliances` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '关联物料号',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '关联供应商',
  `pack_type`     VARCHAR(100) NOT NULL COMMENT '包装器具型号(如小铁箱/塑料周转箱)',
  `pack_capacity` INT NOT NULL COMMENT '标准包装满载容量数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 8. 供应商名录表
CREATE TABLE IF NOT EXISTS `suppliers` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `supplier_code`  VARCHAR(100) NOT NULL UNIQUE COMMENT '供应商唯一代码',
  `supplier_name`  VARCHAR(255) NOT NULL COMMENT '供应商企业名称',
  `contact_name`   VARCHAR(100) DEFAULT '' COMMENT '联系人',
  `contact_phone`  VARCHAR(50) DEFAULT '' COMMENT '联系电话',
  `created_by`     VARCHAR(100) DEFAULT 'system',
  `updated_by`     VARCHAR(100) DEFAULT 'system',
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 9. 物理实际库存记录表
CREATE TABLE IF NOT EXISTS `inventories` (
  `id`              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code`   VARCHAR(100) NOT NULL UNIQUE COMMENT '物料号',
  `stock_qty`       INT NOT NULL DEFAULT 0 COMMENT '当前仓库实物库存现存量',
  `min_stock_days`  INT NOT NULL DEFAULT 3 COMMENT '安全低储控制天数',
  `max_stock_days`  INT NOT NULL DEFAULT 15 COMMENT '安全高储积压控制天数',
  `safety_stock`    INT NOT NULL DEFAULT 0 COMMENT '安全库存量（件），用于吸收需求波动',
  `lead_time_days`  INT NOT NULL DEFAULT 7 COMMENT '补货提前期天数（下单→上架全周期）',
  `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 10. 手工入库订单主表
CREATE TABLE IF NOT EXISTS `inbound_orders` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`      VARCHAR(100) NOT NULL UNIQUE COMMENT '手工入库单号(全局唯一)',
  `status`        VARCHAR(50) NOT NULL DEFAULT '未入库' COMMENT '入库状态: 未入库 / 已完成',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '对应发货供应商',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 11. 入库单行项目明细表
CREATE TABLE IF NOT EXISTS `inbound_details` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细主键',
  `inbound_id`    BIGINT NOT NULL COMMENT '关联主表自增ID',
  `order_no`      VARCHAR(100) NOT NULL COMMENT '关联业务单号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '入库零件号',
  `pack_capacity` INT NOT NULL COMMENT '包装容量快照',
  `plan_qty`      INT NOT NULL COMMENT '计划入库总数',
  `actual_qty`    INT NOT NULL DEFAULT 0 COMMENT '到货现场手工确认实际核销数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 12. 物料出库单主表
CREATE TABLE IF NOT EXISTS `outbound_orders` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`   VARCHAR(100) NOT NULL UNIQUE COMMENT '出库业务单号',
  `status`     VARCHAR(50) NOT NULL DEFAULT '未出库' COMMENT '出库单状态: 未出库 / 已完成',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 13. 出库单行项目明细表
CREATE TABLE IF NOT EXISTS `outbound_details` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
  `outbound_id`   BIGINT NOT NULL COMMENT '主表ID',
  `order_no`      VARCHAR(100) NOT NULL COMMENT '主单号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '零件号',
  `pack_capacity` INT NOT NULL COMMENT '出库单器具容量快照',
  `plan_qty`      INT NOT NULL COMMENT '计划领料出库数量',
  `actual_qty`    INT NOT NULL DEFAULT 0 COMMENT '仓库实际下架手工清点确认数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 14. 出库批次流水表
CREATE TABLE IF NOT EXISTS `outbound_histories` (
  `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `outbound_id`        BIGINT NOT NULL COMMENT '出库单主表ID',
  `outbound_order_no`  VARCHAR(100) NOT NULL COMMENT '出库单号',
  `outbound_detail_id` BIGINT NOT NULL COMMENT '出库明细ID',
  `material_code`      VARCHAR(100) NOT NULL COMMENT '物料号',
  `inbound_id`         BIGINT NOT NULL COMMENT '来源入库单主表ID',
  `inbound_order_no`   VARCHAR(100) NOT NULL COMMENT '来源入库单号',
  `inbound_detail_id`  BIGINT NOT NULL COMMENT '来源入库明细ID',
  `barcode_id`         BIGINT NOT NULL COMMENT '实际出库二维码ID',
  `barcode`            VARCHAR(150) NOT NULL COMMENT '实际出库看板号',
  `deduct_qty`         INT NOT NULL COMMENT '本次扣减数量',
  `created_at`         DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 15. 物料器具二维码追踪表
CREATE TABLE IF NOT EXISTS `barcodes` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '零件编码',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '生产供应商 / OUT',
  `barcode`       VARCHAR(150) NOT NULL UNIQUE COMMENT '唯一箱单标签看板号',
  `inbound_id`    BIGINT DEFAULT NULL COMMENT '关联入库单主键ID（入库二维码）/ 关联出库单主键ID（出库标签）',
  `type`          VARCHAR(20) NOT NULL DEFAULT 'inbound' COMMENT '二维码类型: inbound(入库二维码) / outbound(出库标签)',
  `status`        VARCHAR(50) NOT NULL DEFAULT '待入库' COMMENT '二维码生命周期: 待入库/在库/已出库(入库二维码) | 待出库/已出库(出库标签)',
  `remaining_qty` INT NOT NULL DEFAULT 0 COMMENT '当前剩余数量（入库二维码拆箱后余量，出库标签为单箱数量）',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 16. AI库存推演与智能决策报告表
CREATE TABLE IF NOT EXISTS `ai_inventory_reports` (
  `id`                       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键报告ID',
  `material_code`            VARCHAR(100) NOT NULL COMMENT '预测目标物料号',
  `current_stock`            INT NOT NULL COMMENT '预测切片时的物理库存数量快照',
  `risk_type`                VARCHAR(50) NOT NULL COMMENT '大模型研判类型: NORMAL / LOW_STOCK / DEAD_STOCK / BOTH',
  `risk_level`               VARCHAR(50) NOT NULL COMMENT '风险等级: LOW / MEDIUM / HIGH / CRITICAL',
  `analysis_content`         TEXT NOT NULL COMMENT 'AI生成的核心库存演进与根因剖析大段文字',
  `replenishment_suggestion` TEXT NOT NULL COMMENT 'AI给出的物料精益补货控制行动计划描述',
  `suggested_qty`            INT DEFAULT 0 COMMENT 'AI给出的量化推荐补货量(件)',
  `prediction_status`        VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '诊断状态: PENDING / RUNNING / SUCCESS / FAILED',
  `model`                    VARCHAR(100) DEFAULT '' COMMENT '生成报告的模型名称',
  `created_at`               DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`               DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 17. 库存封存记录表
CREATE TABLE IF NOT EXISTS `inventory_freezes` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `barcode_id`    BIGINT NOT NULL COMMENT '关联二维码ID',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编码',
  `barcode`       VARCHAR(150) NOT NULL COMMENT '看板号',
  `freeze_type`   VARCHAR(50) NOT NULL DEFAULT 'QUALITY' COMMENT '封存类型: QUALITY/ADMIN/OTHER',
  `reason`        VARCHAR(255) NOT NULL COMMENT '封存原因',
  `operator`      VARCHAR(100) DEFAULT 'admin' COMMENT '操作人',
  `freeze_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '封存时间',
  `unfreeze_time` DATETIME DEFAULT NULL COMMENT '解封时间',
  `status`        VARCHAR(50) NOT NULL DEFAULT 'FROZEN' COMMENT '状态: FROZEN/UNFROZEN',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ===================================================================
-- 种子数据请使用 seed_data.sql 通过 MySQL 客户端导入:
--   mysql -u root -p smart_wms_dev < seed_data.sql
-- ===================================================================
