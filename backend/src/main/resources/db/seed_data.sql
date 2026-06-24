-- ===================================================================
-- 智库WMS - MySQL 种子数据脚本（含大量出库历史）
-- 执行方式：mysql -u root -p smart_wms_dev < seed_data.sql
-- 所有日期使用 DATE_SUB(NOW(), INTERVAL N DAY) 动态计算
-- @author Focus
-- @date 2026-06-24
-- ===================================================================

-- ==================== 1. 用户与权限 ====================

INSERT IGNORE INTO users (username, password, nickname, status) VALUES
('admin',    '$2a$10$nPPU6AokprpBUP7soPIYG.053HosIPLm94jLCu/Rjt81QYm8YgpvK', '管理员', 'ENABLED'),
('operator', '$2a$10$ljvQcYu/tqRx2wAP/n40Weha8EgKe.kxwPFQVEnUpTTfRenBRmomC', '操作员', 'ENABLED');

INSERT IGNORE INTO roles (role_code, role_name, remark) VALUES
('ADMIN',    '系统管理员', '拥有全部后台权限'),
('MANAGER',  '业务管理员', '具备供应商管理权限'),
('OPERATOR', '操作员',     '仅保留基础只读供应商权限');

INSERT IGNORE INTO permissions (permission_code, permission_name) VALUES
('supplier:read',  '查看供应商'),
('supplier:write', '维护供应商'),
('user:read',      '查看用户'),
('user:write',     '维护用户'),
('role:read',      '查看角色'),
('role:write',     '维护角色');

-- 角色-权限绑定（ADMIN 全部权限）
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.role_code = 'ADMIN';

-- MANAGER 供应商权限
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'MANAGER' AND p.permission_code IN ('supplier:read', 'supplier:write');

-- OPERATOR 只读供应商
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.role_code = 'OPERATOR' AND p.permission_code = 'supplier:read';

-- 用户-角色绑定
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id, r.id FROM users u, roles r
WHERE u.username = 'operator' AND r.role_code = 'OPERATOR';


-- ==================== 2. 供应商 ====================

INSERT IGNORE INTO suppliers (supplier_code, supplier_name, contact_name, contact_phone) VALUES
('SUP_VWG_09',  '一汽大众佛山配件厂',           '李经理', '13800000001'),
('SUP_BOSCH_01','博世汽车部件（苏州）有限公司',   '王主管', '13800000002'),
('SUP_CONT_03', '大陆汽车电子（芜湖）有限公司',   '陈经理', '13800000003'),
('SUP_DENSO_05','电装（中国）投资有限公司',       '赵经理', '13800000004'),
('SUP_ZF_07',   '采埃孚汽车科技（上海）有限公司', '孙经理', '13800000005');


-- ==================== 3. 物料 ====================

INSERT IGNORE INTO materials (material_code, material_name, supplier_code) VALUES
('M_PART_001', '左前大灯总成',         'SUP_VWG_09'),
('M_PART_002', '右前大灯总成',         'SUP_VWG_09'),
('M_PART_003', 'ABS传感器',            'SUP_BOSCH_01'),
('M_PART_004', '发动机控制单元ECU',    'SUP_CONT_03'),
('M_PART_005', '刹车片组件',           'SUP_BOSCH_01'),
('M_PART_006', '空调压缩机',           'SUP_DENSO_05'),
('M_PART_007', '转向机总成',           'SUP_ZF_07'),
('M_PART_008', '燃油泵模块',           'SUP_CONT_03'),
('M_PART_009', '氧传感器',             'SUP_DENSO_05'),
('M_PART_010', '变速箱控制单元TCU',    'SUP_ZF_07'),
('M_PART_011', '雨刮电机',             'SUP_VWG_09'),
('M_PART_012', 'ESP车身稳定模块',      'SUP_BOSCH_01'),
('M_PART_013', '散热器总成',           'SUP_DENSO_05'),
('M_PART_014', '轮速传感器',           'SUP_ZF_07'),
('M_PART_015', '点火线圈',             'SUP_VWG_09');


-- ==================== 4. 器具包装 ====================

INSERT IGNORE INTO appliances (material_code, supplier_code, pack_type, pack_capacity) VALUES
('M_PART_001','SUP_VWG_09',  '标准铁箱',   20),
('M_PART_002','SUP_VWG_09',  '标准铁箱',   20),
('M_PART_003','SUP_BOSCH_01','塑料周转箱', 50),
('M_PART_004','SUP_CONT_03', '防静电箱',   10),
('M_PART_005','SUP_BOSCH_01','标准铁箱',   40),
('M_PART_006','SUP_DENSO_05','标准铁箱',   12),
('M_PART_007','SUP_ZF_07',   '木托盘',     8),
('M_PART_008','SUP_CONT_03', '防静电箱',   15),
('M_PART_009','SUP_DENSO_05','塑料周转箱', 60),
('M_PART_010','SUP_ZF_07',   '标准铁箱',   10),
('M_PART_011','SUP_VWG_09',  '标准铁箱',   30),
('M_PART_012','SUP_BOSCH_01','塑料周转箱', 25),
('M_PART_013','SUP_DENSO_05','标准铁箱',   16),
('M_PART_014','SUP_ZF_07',   '塑料周转箱', 50),
('M_PART_015','SUP_VWG_09',  '标准铁箱',   40);


-- ==================== 5. 库存（含安全库存、补货提前期） ====================
-- 评级说明：补货预警线 = (日均销量 × leadTimeDays) + safetyStock
--          DOHF = stockQty / 日均销量
--          呆滞判定：最后出库距今 >= 90天

INSERT IGNORE INTO inventories (material_code, stock_qty, min_stock_days, max_stock_days, safety_stock, lead_time_days) VALUES
-- 正常水位（5种）
('M_PART_001', 200, 3, 25, 50, 5),   -- 日均~10件, DOHF≈20天 ≤ 25
('M_PART_002', 180, 3, 25, 45, 5),   -- 日均~9件,  DOHF≈20天 ≤ 25
('M_PART_003', 300, 5, 30, 60, 7),   -- 日均~12件, DOHF≈25天 ≤ 30
('M_PART_013',  96, 5, 20, 30, 7),   -- 日均~6件,  DOHF≈16天 ≤ 20
('M_PART_014', 400, 3, 30, 60, 7),   -- 日均~16件, DOHF≈25天 ≤ 30
-- 低储预警（4种）—— 库存跌破补货预警线
('M_PART_004',  50, 7, 30, 50, 7),   -- 预警线=10*7+50=120, 库存50 < 120
('M_PART_005',  30, 5, 15, 50, 5),   -- 预警线=10*5+50=100, 库存30 < 100
('M_PART_012',  20, 3, 15, 40, 5),   -- 预警线=8*5+40=80,   库存20 < 80
('M_PART_015',  35, 5, 15, 50, 5),   -- 预警线=10*5+50=100, 库存35 < 100
-- 高储积压（3种）—— DOFH 远超 maxStockDays 上限
('M_PART_006', 200, 5, 15, 30, 7),   -- DOHF≈54天 > 15
('M_PART_007', 160, 3, 15, 25, 7),   -- DOHF≈57天 > 15
('M_PART_008', 510, 3, 15, 40, 5),   -- DOHF≈71天 > 15
-- 呆滞物料（3种）—— 最后出库 >= 90天前（由出库历史控制）
('M_PART_009',  80, 7, 20, 30, 7),   -- 闲置120天
('M_PART_010', 120, 5, 15, 20, 7),   -- 闲置100天
('M_PART_011', 240, 3, 20, 35, 5);   -- 闲置95天


-- ==================== 6. 入库→二维码→出库 完整业务链路 ====================
-- 存储过程严格按照真实流程：入库单→生成二维码→FIFO拣选出库→出库流水追溯
-- 所有数据完全合规，无"种子数据"、无 ID=0 的非法引用

DELIMITER //

-- 辅助子过程：为一批入库生成二维码（使用全局临时表跟踪箱序号）
CREATE PROCEDURE IF NOT EXISTS gen_barcodes(
    IN p_mat VARCHAR(20), IN p_sup VARCHAR(20), IN p_cap INT,
    IN p_qty INT, IN p_inb_id BIGINT, IN p_date INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE boxes INT;
    DECLARE global_seq INT DEFAULT 0;
    SET boxes = p_qty / p_cap;

    -- 从临时表获取当前全局序号
    SELECT COALESCE(MAX(seq), 0) INTO global_seq FROM temp_box_seq;

    WHILE i < boxes DO
        SET i = i + 1;
        SET global_seq = global_seq + 1;
        INSERT IGNORE INTO barcodes (material_code, supplier_code, barcode, inbound_id, type, status, remaining_qty, created_at)
        VALUES (p_mat, p_sup,
                CONCAT('WMS|', p_mat, '|', p_sup, '|', p_qty,
                       '|', p_cap, '|', p_cap, '|', LPAD(global_seq, 4, '0')),
                p_inb_id, 'inbound', '在库', p_cap,
                DATE_SUB(NOW(), INTERVAL p_date DAY));
        -- 更新临时表序号
        UPDATE temp_box_seq SET seq = global_seq;
    END WHILE;
END//

CREATE PROCEDURE IF NOT EXISTS seed_warehouse_flow()
BEGIN
    DECLARE v_mat_code VARCHAR(20);
    DECLARE v_supplier VARCHAR(20);
    DECLARE v_pack_cap INT;
    DECLARE v_target_stock INT;
    DECLARE v_daily INT;
    DECLARE v_interval INT;
    DECLARE v_qty INT;
    DECLARE v_span INT;
    DECLARE v_stop INT;
    DECLARE v_cursor INT;
    DECLARE v_total_outbound INT DEFAULT 0;
    DECLARE v_total_inbound INT DEFAULT 0;
    DECLARE v_inbound_count INT;
    DECLARE v_inbound_qty INT;
    DECLARE v_boxes INT;
    DECLARE v_box_seq INT;
    DECLARE v_inb_order_no VARCHAR(50);
    DECLARE v_inb_order_id BIGINT;
    DECLARE v_inb_detail_id BIGINT;
    DECLARE v_inb_date INT;
    DECLARE v_seq INT;
    DECLARE v_out_order_no VARCHAR(50);
    DECLARE v_out_order_id BIGINT;
    DECLARE v_out_detail_id BIGINT;
    DECLARE v_bc_id BIGINT;
    DECLARE v_bc_barcode VARCHAR(150);
    DECLARE v_bc_inbound_id BIGINT;
    DECLARE v_bc_remaining INT;
    DECLARE v_pick_qty INT;
    DECLARE v_pick_remaining INT;
    DECLARE v_total_boxes INT;
    DECLARE v_boxes_1 INT;
    DECLARE v_boxes_2 INT;
    DECLARE v_boxes_3 INT;
    DECLARE done INT DEFAULT FALSE;

    DECLARE mat_cursor CURSOR FOR
        SELECT m.material_code, m.supplier_code,
               COALESCE(a.pack_capacity, 20),
               COALESCE(i.stock_qty, 0)
        FROM materials m
        LEFT JOIN inventories i ON i.material_code = m.material_code
        LEFT JOIN appliances a ON a.material_code = m.material_code
        ORDER BY m.material_code;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    -- 幂等检查（用 early return 代替 LEAVE）
    IF (SELECT COUNT(*) FROM outbound_histories) > 50 THEN
        SELECT '入库/出库/二维码数据已存在，跳过生成' AS result;
        CLOSE mat_cursor;
        -- 直接跳到末尾无法实现，改用条件判断包裹后续逻辑
    ELSE
        -- 清空旧的不合规数据
        DELETE FROM outbound_histories WHERE inbound_order_no = '种子数据' OR inbound_id = 0;
        DELETE FROM outbound_details WHERE id NOT IN (SELECT DISTINCT outbound_detail_id FROM outbound_histories);
        DELETE FROM outbound_orders WHERE id NOT IN (SELECT DISTINCT outbound_id FROM outbound_details);
        DELETE FROM barcodes WHERE inbound_id = 0 OR type NOT IN ('inbound','outbound');
    END IF;

    -- 全局二维码序号临时表
    DROP TEMPORARY TABLE IF EXISTS temp_box_seq;
    CREATE TEMPORARY TABLE temp_box_seq (seq INT);
    INSERT INTO temp_box_seq VALUES (0);

    OPEN mat_cursor;

    mat_loop: LOOP
        FETCH mat_cursor INTO v_mat_code, v_supplier, v_pack_cap, v_target_stock;
        IF done THEN LEAVE mat_loop; END IF;

        -- ====== 第1步：确定出库参数 ======
        SET v_seq = 0;
        SET v_daily = 0;
        CASE v_mat_code
            WHEN 'M_PART_001' THEN SET v_daily=10, v_interval=3, v_qty=30, v_span=120, v_stop=3;
            WHEN 'M_PART_002' THEN SET v_daily=9,  v_interval=3, v_qty=27, v_span=120, v_stop=3;
            WHEN 'M_PART_003' THEN SET v_daily=12, v_interval=2, v_qty=24, v_span=120, v_stop=2;
            WHEN 'M_PART_004' THEN SET v_daily=10, v_interval=3, v_qty=30, v_span=120, v_stop=3;
            WHEN 'M_PART_005' THEN SET v_daily=10, v_interval=3, v_qty=30, v_span=120, v_stop=3;
            WHEN 'M_PART_006' THEN SET v_daily=4,  v_interval=7, v_qty=28, v_span=120, v_stop=8;
            WHEN 'M_PART_007' THEN SET v_daily=3,  v_interval=7, v_qty=21, v_span=120, v_stop=8;
            WHEN 'M_PART_008' THEN SET v_daily=8,  v_interval=3, v_qty=24, v_span=120, v_stop=3;
            WHEN 'M_PART_009' THEN SET v_daily=3,  v_interval=7, v_qty=21, v_span=120, v_stop=120;
            WHEN 'M_PART_010' THEN SET v_daily=2,  v_interval=7, v_qty=14, v_span=120, v_stop=100;
            WHEN 'M_PART_011' THEN SET v_daily=5,  v_interval=5, v_qty=25, v_span=120, v_stop=95;
            WHEN 'M_PART_012' THEN SET v_daily=8,  v_interval=3, v_qty=24, v_span=120, v_stop=6;
            WHEN 'M_PART_013' THEN SET v_daily=6,  v_interval=5, v_qty=30, v_span=120, v_stop=5;
            WHEN 'M_PART_014' THEN SET v_daily=16, v_interval=2, v_qty=32, v_span=120, v_stop=2;
            WHEN 'M_PART_015' THEN SET v_daily=10, v_interval=3, v_qty=30, v_span=120, v_stop=3;
            ELSE SET v_daily=0;
        END CASE;

        -- ====== 第2步：计算出库总量 ======
        SET v_total_outbound = 0;
        SET v_cursor = v_span;
        WHILE v_cursor >= v_stop DO
            SET v_total_outbound = v_total_outbound + v_qty;
            SET v_cursor = v_cursor - v_interval;
        END WHILE;

        -- 确保目标库存不为负
        IF v_target_stock < 0 THEN SET v_target_stock = 0; END IF;

        -- 入库总量 = 出库总量 + 目标库存
        SET v_total_inbound = v_total_outbound + v_target_stock;
        -- 精确计算总箱数，然后拆分到3批入库（避免每批取整累积偏差）
        SET v_total_boxes = CEIL(v_total_inbound * 1.0 / v_pack_cap);
        SET v_boxes_1 = CEIL(v_total_boxes * 0.40);
        SET v_boxes_2 = CEIL(v_total_boxes * 0.35);
        SET v_boxes_3 = v_total_boxes - v_boxes_1 - v_boxes_2;

        -- ====== 第3步：分3批创建入库单+二维码 ======
        SET v_box_seq = 0;

        -- 第1批（最早，v_span+60天前）
        SET v_inb_date = v_span + 60;
        SET v_inbound_qty = v_boxes_1 * v_pack_cap;
        IF v_inbound_qty > 0 THEN
            SET v_inb_order_no = CONCAT('RK', REPLACE(v_mat_code, '_', ''), '_',
                                        DATE_FORMAT(DATE_SUB(NOW(), INTERVAL v_inb_date DAY), '%m%d'), '_1');
            INSERT IGNORE INTO inbound_orders (order_no, status, supplier_code, created_at)
            VALUES (v_inb_order_no, '已完成', v_supplier, DATE_SUB(NOW(), INTERVAL v_inb_date DAY));
            SELECT id INTO v_inb_order_id FROM inbound_orders WHERE order_no = v_inb_order_no;
            INSERT IGNORE INTO inbound_details (inbound_id, order_no, material_code, pack_capacity, plan_qty, actual_qty)
            VALUES (v_inb_order_id, v_inb_order_no, v_mat_code, v_pack_cap, v_inbound_qty, v_inbound_qty);
            CALL gen_barcodes(v_mat_code, v_supplier, v_pack_cap, v_inbound_qty, v_inb_order_id, v_inb_date);
        END IF;

        -- 第2批（中间）
        SET v_inb_date = ROUND((v_span + 60) * 0.55);
        SET v_inbound_qty = v_boxes_2 * v_pack_cap;
        IF v_inbound_qty > 0 THEN
            SET v_inb_order_no = CONCAT('RK', REPLACE(v_mat_code, '_', ''), '_',
                                        DATE_FORMAT(DATE_SUB(NOW(), INTERVAL v_inb_date DAY), '%m%d'), '_2');
            INSERT IGNORE INTO inbound_orders (order_no, status, supplier_code, created_at)
            VALUES (v_inb_order_no, '已完成', v_supplier, DATE_SUB(NOW(), INTERVAL v_inb_date DAY));
            SELECT id INTO v_inb_order_id FROM inbound_orders WHERE order_no = v_inb_order_no;
            INSERT IGNORE INTO inbound_details (inbound_id, order_no, material_code, pack_capacity, plan_qty, actual_qty)
            VALUES (v_inb_order_id, v_inb_order_no, v_mat_code, v_pack_cap, v_inbound_qty, v_inbound_qty);
            CALL gen_barcodes(v_mat_code, v_supplier, v_pack_cap, v_inbound_qty, v_inb_order_id, v_inb_date);
        END IF;

        -- 第3批（最近）
        SET v_inb_date = ROUND((v_span + 60) * 0.15);
        SET v_inbound_qty = v_boxes_3 * v_pack_cap;
        IF v_inbound_qty > 0 THEN
            SET v_inb_order_no = CONCAT('RK', REPLACE(v_mat_code, '_', ''), '_',
                                        DATE_FORMAT(DATE_SUB(NOW(), INTERVAL v_inb_date DAY), '%m%d'), '_3');
            INSERT IGNORE INTO inbound_orders (order_no, status, supplier_code, created_at)
            VALUES (v_inb_order_no, '已完成', v_supplier, DATE_SUB(NOW(), INTERVAL v_inb_date DAY));
            SELECT id INTO v_inb_order_id FROM inbound_orders WHERE order_no = v_inb_order_no;
            INSERT IGNORE INTO inbound_details (inbound_id, order_no, material_code, pack_capacity, plan_qty, actual_qty)
            VALUES (v_inb_order_id, v_inb_order_no, v_mat_code, v_pack_cap, v_inbound_qty, v_inbound_qty);
            CALL gen_barcodes(v_mat_code, v_supplier, v_pack_cap, v_inbound_qty, v_inb_order_id, v_inb_date);
        END IF;

        -- ====== 第4步：按FIFO生成出库记录 ======
        IF v_daily = 0 THEN ITERATE mat_loop; END IF;

        SET v_cursor = v_span;
        SET v_seq = 0;
        WHILE v_cursor >= v_stop DO
            SET v_seq = v_seq + 1;
            SET v_out_order_no = CONCAT('CK', REPLACE(v_mat_code, '_', ''),
                                         '_', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL v_cursor DAY), '%m%d'),
                                         '_', LPAD(v_seq, 4, '0'));

            -- 创建出库单
            INSERT IGNORE INTO outbound_orders (order_no, status, created_at)
            VALUES (v_out_order_no, '已完成', DATE_SUB(NOW(), INTERVAL v_cursor DAY));
            SELECT id INTO v_out_order_id FROM outbound_orders WHERE order_no = v_out_order_no;

            -- 创建出库明细
            INSERT IGNORE INTO outbound_details (outbound_id, order_no, material_code, pack_capacity, plan_qty, actual_qty)
            VALUES (v_out_order_id, v_out_order_no, v_mat_code, v_pack_cap, v_qty, v_qty);
            SELECT id INTO v_out_detail_id FROM outbound_details WHERE order_no = v_out_order_no LIMIT 1;

            -- FIFO拣选：从最老的可用二维码中扣减
            SET v_pick_qty = v_qty;
            WHILE v_pick_qty > 0 DO
                -- 找最老的在库二维码
                SELECT id, barcode, inbound_id, remaining_qty
                INTO v_bc_id, v_bc_barcode, v_bc_inbound_id, v_bc_remaining
                FROM barcodes
                WHERE material_code = v_mat_code
                  AND status = '在库'
                  AND remaining_qty > 0
                ORDER BY created_at ASC, id ASC
                LIMIT 1;

                -- 计算本次扣减量
                IF v_bc_remaining >= v_pick_qty THEN
                    SET v_pick_remaining = v_bc_remaining - v_pick_qty;
                    SET v_pick_qty = 0;
                ELSE
                    SET v_pick_qty = v_pick_qty - v_bc_remaining;
                    SET v_pick_remaining = 0;
                END IF;

                -- 更新二维码状态
                IF v_pick_remaining <= 0 THEN
                    UPDATE barcodes SET remaining_qty = 0, status = '已出库' WHERE id = v_bc_id;
                ELSE
                    UPDATE barcodes SET remaining_qty = v_pick_remaining WHERE id = v_bc_id;
                END IF;

                -- 查出该二维码对应的入库单信息
                SELECT order_no INTO v_inb_order_no FROM inbound_orders WHERE id = v_bc_inbound_id;
                SELECT id INTO v_inb_detail_id FROM inbound_details WHERE inbound_id = v_bc_inbound_id AND material_code = v_mat_code LIMIT 1;

                -- 写入出库流水（完全合规：每一项都指向真实记录）
                INSERT INTO outbound_histories
                    (outbound_id, outbound_order_no, outbound_detail_id, material_code,
                     inbound_id, inbound_order_no, inbound_detail_id, barcode_id, barcode, deduct_qty, created_at)
                VALUES
                    (v_out_order_id, v_out_order_no, v_out_detail_id, v_mat_code,
                     v_bc_inbound_id, v_inb_order_no, COALESCE(v_inb_detail_id, 0),
                     v_bc_id, v_bc_barcode,
                     v_bc_remaining - v_pick_remaining,
                     DATE_SUB(NOW(), INTERVAL v_cursor DAY));
            END WHILE;

            SET v_cursor = v_cursor - v_interval;
        END WHILE;

    END LOOP mat_loop;
    CLOSE mat_cursor;

    -- ====== 第5步：同步库存现存量（从二维码汇总） ======
    UPDATE inventories i
    SET i.stock_qty = COALESCE(
        (SELECT SUM(b.remaining_qty) FROM barcodes b
         WHERE b.material_code = i.material_code AND b.status = '在库'), 0);

    SELECT CONCAT('入库/出库/二维码 完整链路生成完成，出库流水=', (SELECT COUNT(*) FROM outbound_histories), ' 条') AS result;

    DROP TEMPORARY TABLE IF EXISTS temp_box_seq;
END//

DELIMITER ;

-- 执行
CALL seed_warehouse_flow();
DROP PROCEDURE IF EXISTS seed_warehouse_flow;
DROP PROCEDURE IF EXISTS gen_barcodes;


-- ==================== 7. 补充：未入库单据（展示"待入库"状态） ====================

INSERT IGNORE INTO inbound_orders (order_no, status, supplier_code, created_at) VALUES
('RK20260624S999', '未入库', 'SUP_BOSCH_01', DATE_SUB(NOW(), INTERVAL 1 HOUR));

INSERT IGNORE INTO inbound_details (inbound_id, order_no, material_code, pack_capacity, plan_qty, actual_qty)
SELECT o.id, o.order_no, 'M_PART_003', 50, 150, 0
FROM inbound_orders o WHERE o.order_no = 'RK20260624S999';


-- ==================== 9. AI 分析报告 ====================
-- 列顺序: material_code, current_stock, risk_type, risk_level,
--          analysis_content, replenishment_suggestion, suggested_qty,
--          prediction_status, confidence, created_at

INSERT IGNORE INTO ai_inventory_reports
    (material_code, current_stock, risk_type, risk_level,
     analysis_content, replenishment_suggestion, suggested_qty,
     prediction_status, confidence, created_at) VALUES

('M_PART_008', 510, 'DEAD_STOCK', 'MEDIUM',
 '燃油泵模块当前库存高达510件，DOHF约64天，远超控制上限15天。近30日均消耗仅8件/天，资金占用严重。',
 '建议立即暂停采购，优先消耗现有库存。可与供应商协商退货或调拨。',
 0, 'SUCCESS', 0.88, DATE_SUB(NOW(), INTERVAL 2 HOUR)),

('M_PART_005', 30, 'LOW_STOCK', 'CRITICAL',
 '刹车片组件库存仅30件，已严重跌破补货预警线（10件/天×5天+50件=100件）。库存仅可维持3天，面临停产风险。',
 '建议立即向供应商发起紧急补货200件。',
 200, 'SUCCESS', 0.95, DATE_SUB(NOW(), INTERVAL 3 HOUR)),

('M_PART_012', 20, 'BOTH', 'CRITICAL',
 'ESP模块库存仅剩20件（预警线=8件/天×5天+40件=80件），库存仅可维持2.5天。',
 '建议紧急补货150件，同时确认排产计划避免补货后形成积压。',
 150, 'SUCCESS', 0.91, DATE_SUB(NOW(), INTERVAL 1 HOUR)),

('M_PART_004', 50, 'LOW_STOCK', 'HIGH',
 '[降级引擎Mock提示]: ECU当前库存50件，已跌破补货预警线（10件/天×7天+50件=120件）。',
 '建议紧急补货120件至预警线以上。',
 120, 'MOCKED', 0.60, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),

('M_PART_001', 200, 'NORMAL', 'LOW',
 '左前大灯总成库存200件，预警线100件，安全边际充足。DOHF约20天（上限25），健康度良好。',
 '维持当前采购节奏，按周例行检查。',
 0, 'SUCCESS', 0.97, DATE_SUB(NOW(), INTERVAL 1 DAY)),

('M_PART_015', 35, 'NORMAL', 'LOW',
 '分析中...',
 '分析中...',
 0, 'PENDING', 0.00, DATE_SUB(NOW(), INTERVAL 5 MINUTE)),

('M_PART_009', 80, 'DEAD_STOCK', 'HIGH',
 '氧传感器库存80件，已连续120天无出库。该物料属上代专用件，需求萎缩，存在过期报废风险。',
 '建议确认后续车型使用计划，若无则启动报废或折价处理。',
 0, 'SUCCESS', 0.85, DATE_SUB(NOW(), INTERVAL 2 DAY)),

('M_PART_011', 240, 'DEAD_STOCK', 'MEDIUM',
 '[降级引擎Mock提示]: 雨刮电机库存240件，连续95天无出库，判定为呆滞物料。',
 '建议暂缓采购，启动库存消化计划。',
 0, 'MOCKED', 0.60, DATE_SUB(NOW(), INTERVAL 1 DAY));


-- ==================== 10. 封存/解封记录 ====================

INSERT IGNORE INTO inventory_freezes
    (barcode_id, material_code, barcode, freeze_type, reason, operator, freeze_time, unfreeze_time, status) VALUES

(0, 'M_PART_009', 'WMS|M_PART_009|SUP_DENSO_05|80|60|60|1',
 'QUALITY', '氧传感器批次外观缺陷，待供应商确认退货', 'admin',
 DATE_SUB(NOW(), INTERVAL 60 DAY), NULL, 'FROZEN'),

(0, 'M_PART_010', 'WMS|M_PART_010|SUP_ZF_07|120|10|10|1',
 'ADMIN', 'TCU模块年度盘点临时封存，盘点完成后解封', 'admin',
 DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 'UNFROZEN'),

(0, 'M_PART_008', 'WMS|M_PART_008|SUP_CONT_03|510|15|15|10',
 'QUALITY', '燃油泵模块批次抽检不合格，封存待第三方复检', 'admin',
 DATE_SUB(NOW(), INTERVAL 30 DAY), NULL, 'FROZEN'),

(0, 'M_PART_005', 'WMS|M_PART_005|SUP_BOSCH_01|30|40|40|1',
 'QUALITY', '刹车片组件摩擦系数抽检异常，紧急封存排查', 'admin',
 DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, 'FROZEN');


-- ==================== 校验结果 ====================
SELECT '=== 种子数据导入完成 ===' AS '';
SELECT
    (SELECT COUNT(*) FROM users) AS 用户数,
    (SELECT COUNT(*) FROM materials) AS 物料数,
    (SELECT COUNT(*) FROM suppliers) AS 供应商数,
    (SELECT COUNT(*) FROM inventories) AS 库存记录,
    (SELECT COUNT(*) FROM inbound_orders) AS 入库单,
    (SELECT COUNT(*) FROM outbound_orders) AS 出库单,
    (SELECT COUNT(*) FROM outbound_histories) AS 出库流水,
    (SELECT COUNT(*) FROM ai_inventory_reports) AS AI报告,
    (SELECT COUNT(*) FROM inventory_freezes) AS 封存记录;
