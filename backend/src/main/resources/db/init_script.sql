-- ===================================================================
-- 智库WMS - 数据库初始化脚本（11 张表）
-- 种子数据由 DataInitializer 通过 JDBC 插入，避免 H2 编码问题
-- @author Focus
-- @date 2026-06-03
-- ===================================================================

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS `users` (
  `user_id`    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  `username`   VARCHAR(100) NOT NULL UNIQUE COMMENT '登录账号',
  `password`   VARCHAR(255) NOT NULL COMMENT 'BCrypt加密散列密码',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 物料基础档案表
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

-- 3. 器具包装参数表
CREATE TABLE IF NOT EXISTS `appliances` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '关联物料号',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '关联供应商',
  `pack_type`     VARCHAR(100) NOT NULL COMMENT '包装器具型号(如小铁箱/塑料周转箱)',
  `pack_capacity` INT NOT NULL COMMENT '标准包装满载容量数量',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 4. 供应商名录表
CREATE TABLE IF NOT EXISTS `suppliers` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `supplier_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '供应商唯一代码',
  `supplier_name` VARCHAR(255) NOT NULL COMMENT '供应商企业名称',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 5. 物理实际库存记录表
CREATE TABLE IF NOT EXISTS `inventories` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code`  VARCHAR(100) NOT NULL UNIQUE COMMENT '物料号',
  `stock_qty`      INT NOT NULL DEFAULT 0 COMMENT '当前仓库实物库存现存量',
  `min_stock_days` INT NOT NULL DEFAULT 3 COMMENT '安全低储控制天数',
  `max_stock_days` INT NOT NULL DEFAULT 15 COMMENT '安全高储积压控制天数',
  `created_at`     DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 6. 手工入库订单主表
CREATE TABLE IF NOT EXISTS `inbound_orders` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`      VARCHAR(100) NOT NULL UNIQUE COMMENT '手工入库单号(全局唯一)',
  `status`        VARCHAR(50) NOT NULL DEFAULT '未入库' COMMENT '入库状态: 未入库 / 已完成',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '对应发货供应商',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 7. 入库单行项目明细表
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

-- 8. 物料出库单主表
CREATE TABLE IF NOT EXISTS `outbound_orders` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `order_no`   VARCHAR(100) NOT NULL UNIQUE COMMENT '出库业务单号',
  `status`     VARCHAR(50) NOT NULL DEFAULT '未出库' COMMENT '出库单状态: 未出库 / 已完成',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 9. 出库单行项目明细表
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

-- 10. 物料器具条码追踪表
CREATE TABLE IF NOT EXISTS `barcodes` (
  `id`            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '零件编码',
  `supplier_code` VARCHAR(100) NOT NULL COMMENT '生产供应商',
  `barcode`       VARCHAR(150) NOT NULL UNIQUE COMMENT '唯一箱单标签条码号',
  `status`        VARCHAR(50) NOT NULL DEFAULT '待入库' COMMENT '条码生命周期: 待入库 / 在库 / 已出库',
  `created_at`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 11. AI库存推演与智能决策报告表
CREATE TABLE IF NOT EXISTS `ai_inventory_reports` (
  `id`                       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键报告ID',
  `material_code`            VARCHAR(100) NOT NULL COMMENT '预测目标物料号',
  `current_stock`            INT NOT NULL COMMENT '预测切片时的物理库存数量快照',
  `risk_type`                VARCHAR(50) NOT NULL COMMENT '大模型研判类型: NORMAL / LOW_STOCK / DEAD_STOCK / BOTH',
  `risk_level`               VARCHAR(50) NOT NULL COMMENT '风险等级: LOW / MEDIUM / HIGH / CRITICAL',
  `analysis_content`         TEXT NOT NULL COMMENT 'AI生成的核心库存演进与根因剖析大段文字',
  `replenishment_suggestion` TEXT NOT NULL COMMENT 'AI给出的物料精益补货控制行动计划描述',
  `suggested_qty`            INT DEFAULT 0 COMMENT 'AI给出的量化推荐补货量(件)',
  `prediction_status`        VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '异步诊断进度: PENDING / RUNNING / SUCCESS / MOCKED',
  `confidence`               FLOAT DEFAULT 1.0 COMMENT '模型输出可信度得分',
  `created_at`               DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at`               DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ===================================================================
-- 种子数据由 DataInitializer.java 通过 JDBC 插入
-- 避免 SQL 文件编码导致 H2 中文乱码
-- ===================================================================
