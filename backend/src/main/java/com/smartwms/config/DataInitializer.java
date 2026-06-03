/**
 * 应用启动时的默认数据初始化器。
 * 通过 JDBC/MyBatis-Plus 插入覆盖各类前端展示场景的丰富种子数据。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.entity.*;
import com.smartwms.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";
    private static final String OPERATOR_USERNAME = "operator";
    private static final String OPERATOR_PASSWORD = "operator123";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final ApplianceMapper applianceMapper;
    private final InventoryMapper inventoryMapper;
    private final AiReportMapper aiReportMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;

    public DataInitializer(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder,
                           SupplierMapper supplierMapper, MaterialMapper materialMapper,
                           ApplianceMapper applianceMapper, InventoryMapper inventoryMapper,
                           AiReportMapper aiReportMapper, InboundOrderMapper inboundOrderMapper,
                           InboundDetailMapper inboundDetailMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.applianceMapper = applianceMapper;
        this.inventoryMapper = inventoryMapper;
        this.aiReportMapper = aiReportMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
    }

    @Override
    public void run(String... args) {
        log.info("========== [初始化] 开始装载种子数据 ==========");

        // 1. 用户
        createUserIfNotExists(DEFAULT_USERNAME, DEFAULT_PASSWORD, "管理员");
        createUserIfNotExists(OPERATOR_USERNAME, OPERATOR_PASSWORD, "操作员");

        // 2. 供应商（5 家）
        seedSuppliers();

        // 3. 物料（15 种，超过分页 10 条阈值）
        seedMaterials();

        // 4. 器具包装
        seedAppliances();

        // 5. 库存（覆盖 NORMAL / LOW_STOCK / HIGH 三种水位）
        seedInventories();

        // 6. AI 报告（覆盖 SUCCESS / MOCKED / PENDING + 各风险类型）
        seedAiReports();

        // 7. 入库单（覆盖未入库 / 已完成）
        seedInboundOrders();

        log.info("========== [初始化] 种子数据装载完毕（"
                + countMaterials() + " 种物料，"
                + countSuppliers() + " 家供应商，"
                + countReports() + " 份AI报告）==========");
    }

    // ==================== 用户 ====================

    private void createUserIfNotExists(String username, String rawPassword, String role) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count == 0) {
            User u = new User();
            u.setUsername(username);
            u.setPassword(passwordEncoder.encode(rawPassword));
            userMapper.insert(u);
            log.info("[初始化] 创建{}账号: {} / {}", role, username, rawPassword);
        }
    }

    // ==================== 供应商 ====================

    private void seedSuppliers() {
        insertSupplier("SUP_VWG_09", "一汽大众佛山配件厂");
        insertSupplier("SUP_BOSCH_01", "博世汽车部件（苏州）有限公司");
        insertSupplier("SUP_CONT_03", "大陆汽车电子（芜湖）有限公司");
        insertSupplier("SUP_DENSO_05", "电装（中国）投资有限公司");
        insertSupplier("SUP_ZF_07", "采埃孚汽车科技（上海）有限公司");
    }

    private void insertSupplier(String code, String name) {
        if (supplierMapper.selectCount(
                new LambdaQueryWrapper<Supplier>().eq(Supplier::getSupplierCode, code)) == 0) {
            Supplier s = new Supplier();
            s.setSupplierCode(code);
            s.setSupplierName(name);
            supplierMapper.insert(s);
        }
    }

    // ==================== 物料 ====================

    private void seedMaterials() {
        insertMaterial("M_PART_001", "左前大灯总成", "SUP_VWG_09");
        insertMaterial("M_PART_002", "右前大灯总成", "SUP_VWG_09");
        insertMaterial("M_PART_003", "ABS传感器", "SUP_BOSCH_01");
        insertMaterial("M_PART_004", "发动机控制单元ECU", "SUP_CONT_03");
        insertMaterial("M_PART_005", "刹车片组件", "SUP_BOSCH_01");
        insertMaterial("M_PART_006", "空调压缩机", "SUP_DENSO_05");
        insertMaterial("M_PART_007", "转向机总成", "SUP_ZF_07");
        insertMaterial("M_PART_008", "燃油泵模块", "SUP_CONT_03");
        insertMaterial("M_PART_009", "氧传感器", "SUP_DENSO_05");
        insertMaterial("M_PART_010", "变速箱控制单元TCU", "SUP_ZF_07");
        insertMaterial("M_PART_011", "雨刮电机", "SUP_VWG_09");
        insertMaterial("M_PART_012", "ESP车身稳定模块", "SUP_BOSCH_01");
        insertMaterial("M_PART_013", "散热器总成", "SUP_DENSO_05");
        insertMaterial("M_PART_014", "轮速传感器", "SUP_ZF_07");
        insertMaterial("M_PART_015", "点火线圈", "SUP_VWG_09");
    }

    private void insertMaterial(String code, String name, String supplierCode) {
        if (materialMapper.selectCount(
                new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, code)) == 0) {
            Material m = new Material();
            m.setMaterialCode(code);
            m.setMaterialName(name);
            m.setSupplierCode(supplierCode);
            materialMapper.insert(m);
        }
    }

    // ==================== 器具 ====================

    private void seedAppliances() {
        insertAppliance("M_PART_001", "SUP_VWG_09", "标准铁箱", 20);
        insertAppliance("M_PART_002", "SUP_VWG_09", "标准铁箱", 20);
        insertAppliance("M_PART_003", "SUP_BOSCH_01", "塑料周转箱", 50);
        insertAppliance("M_PART_004", "SUP_CONT_03", "防静电箱", 10);
        insertAppliance("M_PART_005", "SUP_BOSCH_01", "标准铁箱", 40);
        insertAppliance("M_PART_006", "SUP_DENSO_05", "标准铁箱", 12);
        insertAppliance("M_PART_007", "SUP_ZF_07", "木托盘", 8);
        insertAppliance("M_PART_008", "SUP_CONT_03", "防静电箱", 15);
        insertAppliance("M_PART_009", "SUP_DENSO_05", "塑料周转箱", 60);
        insertAppliance("M_PART_010", "SUP_ZF_07", "标准铁箱", 10);
        insertAppliance("M_PART_011", "SUP_VWG_09", "标准铁箱", 30);
        insertAppliance("M_PART_012", "SUP_BOSCH_01", "塑料周转箱", 25);
        insertAppliance("M_PART_013", "SUP_DENSO_05", "标准铁箱", 16);
        insertAppliance("M_PART_014", "SUP_ZF_07", "塑料周转箱", 50);
        insertAppliance("M_PART_015", "SUP_VWG_09", "标准铁箱", 40);
    }

    private void insertAppliance(String materialCode, String supplierCode, String type, int cap) {
        if (applianceMapper.selectCount(new LambdaQueryWrapper<Appliance>()
                .eq(Appliance::getMaterialCode, materialCode)
                .eq(Appliance::getSupplierCode, supplierCode)) == 0) {
            Appliance a = new Appliance();
            a.setMaterialCode(materialCode);
            a.setSupplierCode(supplierCode);
            a.setPackType(type);
            a.setPackCapacity(cap);
            applianceMapper.insert(a);
        }
    }

    // ==================== 库存（含水位差异，触发报表颜色高亮） ====================

    /**
     * 日均消耗按 10 件估算（后续由真实出库数据计算）。
     * 低储阈值 = minStockDays × 10，高储阈值 = maxStockDays × 10。
     *
     * 设计意图：
     *   - 超低储（红色）：5 种 → 看板"断供高风险物料数"=5
     *   - 超高储（黄色）：6 种 → 看板"呆滞物料数"=6（含 AI 滞销报告的物料）
     *   - 正常：4 种
     */
    private void seedInventories() {
        // === 超高储（黄色高亮）===
        insertInventory("M_PART_001", 200, 3, 15);  // 200 > 150 → HIGH
        insertInventory("M_PART_002", 180, 3, 15);  // 180 > 150 → HIGH
        insertInventory("M_PART_003", 300, 5, 20);  // 300 > 200 → HIGH
        insertInventory("M_PART_008", 500, 3, 10);  // 500 ≫ 100 严重超高储
        insertInventory("M_PART_011", 250, 3, 15);  // 250 > 150 → HIGH
        insertInventory("M_PART_014", 400, 3, 10);  // 400 ≫ 100 → HIGH

        // === 超低储（红色高亮）===
        insertInventory("M_PART_004", 50, 7, 30);   // 50 < 70 → LOW_STOCK（断供边缘）
        insertInventory("M_PART_005", 15, 3, 15);   // 15 ≪ 30 严重不足
        insertInventory("M_PART_009", 8, 3, 15);    // 8 ≪ 30 即将耗尽
        insertInventory("M_PART_012", 5, 3, 15);    // 5 ≪ 30 极度危急
        insertInventory("M_PART_015", 20, 3, 15);   // 20 < 30 → LOW_STOCK

        // === 正常水位 ===
        insertInventory("M_PART_006", 80, 5, 20);   // 50 < 80 < 200 → NORMAL
        insertInventory("M_PART_007", 35, 3, 12);   // 30 < 35 < 120 → NORMAL
        insertInventory("M_PART_010", 120, 5, 20);  // 50 < 120 < 200 → NORMAL
        insertInventory("M_PART_013", 90, 5, 25);   // 50 < 90 < 250 → NORMAL
    }

    private void insertInventory(String code, int qty, int minDays, int maxDays) {
        if (inventoryMapper.selectCount(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, code)) == 0) {
            Inventory inv = new Inventory();
            inv.setMaterialCode(code);
            inv.setStockQty(qty);
            inv.setMinStockDays(minDays);
            inv.setMaxStockDays(maxDays);
            inventoryMapper.insert(inv);
        }
    }

    // ==================== AI 报告（覆盖全部状态和风险类型） ====================

    private void seedAiReports() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 超高储物料 → AI 诊断为滞销风险
        insertAiReport("M_PART_008", 500, "DEAD_STOCK", "MEDIUM", "SUCCESS", 0, 0.88f,
                "该燃油泵模块当前库存高达500件，远超高储阈值（10天×10件/天=100件）。"
                + "近30日仅出库20件，库存周转天数超过700天，资金占用严重。"
                + "结合未来需求预测，短期内无大规模消耗计划，存在明确的呆滞积压风险。",
                "建议立即暂停该物料采购计划，优先消耗现有库存。可考虑与供应商协商退货或调拨至其他工厂。"
                + "建议将高储天数调整至10天以内，建立库存消化跟踪机制。",
                now.minusHours(2));

        // 2. 严重超低储 → AI 建议紧急补货
        insertAiReport("M_PART_005", 15, "LOW_STOCK", "CRITICAL", "SUCCESS", 200, 0.95f,
                "刹车片组件当前库存仅15件，已严重跌破低储安全线（3天×10件/天=30件）。"
                + "按未来15天排产预测（总需求160件），库存将在1.5天内完全耗尽，"
                + "导致总装线停产风险。属于最高优先级断供预警。",
                "建议立即向供应商SUP_BOSCH_01发起紧急补货订单。推荐补货量200件（标准铁箱5箱），"
                + "预计可恢复至15天安全水位。建议同步启用安全库存缓冲机制。",
                now.minusHours(3));

        // 3. 另一超低储 → AI 双重风险（断供+滞销都有迹象）
        insertAiReport("M_PART_012", 5, "BOTH", "CRITICAL", "SUCCESS", 150, 0.91f,
                "ESP车身稳定模块库存仅剩5件，已触发极端低储警报。同时该物料近15天无出库记录，"
                + "存在需求波动导致的供需错配风险——若需求突然启动，将无法满足。",
                "建议紧急补货150件至基础安全线。同时与计划部门确认未来两周是否有该车型排产计划，"
                + "若无则标记为潜在呆滞观察对象。",
                now.minusHours(1));

        // 4. Mock 兜底报告（大模型超时降级产物）
        insertAiReport("M_PART_009", 8, "LOW_STOCK", "HIGH", "MOCKED", 92, 0.6f,
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。"
                + "当前库存已跌破低储天数标准线（3天×10件/天=30件，现仅8件），"
                + "预测未来需求存在供应缺口，产生基础断供风险。",
                "建议向供应商发起紧急补货。推荐补货量：92件，可将库存恢复至低储安全线以上。",
                now.minusMinutes(30));

        // 5. 正常物料 → AI 确认无风险
        insertAiReport("M_PART_006", 80, "NORMAL", "LOW", "SUCCESS", 0, 0.97f,
                "空调压缩机当前库存80件，处于安全水位区间（低储50件~高储200件）。"
                + "近30日出库稳定，日均消耗约8件，库存可维持约10天。未来需求预测无明显波动。"
                + "整体库存健康度良好，无需干预。",
                "维持当前采购节奏，按周例行检查库存水位即可。",
                now.minusDays(1));

        // 6. PENDING 状态报告（模拟正在等待 AI 分析）
        insertAiReport("M_PART_015", 20, "NORMAL", "LOW", "PENDING", 0, 0f,
                "分析中...", "分析中...", now.minusMinutes(5));
    }

    private void insertAiReport(String materialCode, int stock, String riskType,
                                 String riskLevel, String status, int suggestedQty,
                                 float confidence, String analysis, String suggestion,
                                 LocalDateTime createdAt) {
        // AI 报告允许多条（每次预测生成新记录），不判重
        AiReport r = new AiReport();
        r.setMaterialCode(materialCode);
        r.setCurrentStock(stock);
        r.setRiskType(riskType);
        r.setRiskLevel(riskLevel);
        r.setPredictionStatus(status);
        r.setSuggestedQty(suggestedQty);
        r.setConfidence(confidence);
        r.setAnalysisContent(analysis);
        r.setReplenishmentSuggestion(suggestion);
        r.setCreatedAt(createdAt);
        r.setUpdatedAt(createdAt);
        aiReportMapper.insert(r);
    }

    // ==================== 入库单（覆盖未入库/已完成状态） ====================

    private void seedInboundOrders() {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 已完成入库单
        if (inboundOrderMapper.selectCount(null) == 0) {
            // 单1：已完成
            InboundOrder o1 = new InboundOrder();
            o1.setOrderNo("RK" + today + "001");
            o1.setStatus("已完成");
            o1.setSupplierCode("SUP_VWG_09");
            o1.setCreatedAt(now.minusHours(5));
            inboundOrderMapper.insert(o1);

            InboundDetail d1 = new InboundDetail();
            d1.setInboundId(o1.getId());
            d1.setOrderNo(o1.getOrderNo());
            d1.setMaterialCode("M_PART_001");
            d1.setPackCapacity(20);
            d1.setPlanQty(200);
            d1.setActualQty(200);
            inboundDetailMapper.insert(d1);

            // 单2：未入库
            InboundOrder o2 = new InboundOrder();
            o2.setOrderNo("RK" + today + "002");
            o2.setStatus("未入库");
            o2.setSupplierCode("SUP_BOSCH_01");
            o2.setCreatedAt(now.minusHours(1));
            inboundOrderMapper.insert(o2);

            InboundDetail d2 = new InboundDetail();
            d2.setInboundId(o2.getId());
            d2.setOrderNo(o2.getOrderNo());
            d2.setMaterialCode("M_PART_003");
            d2.setPackCapacity(50);
            d2.setPlanQty(150);
            d2.setActualQty(0);
            inboundDetailMapper.insert(d2);

            // 单3：已完成
            InboundOrder o3 = new InboundOrder();
            o3.setOrderNo("RK" + today + "003");
            o3.setStatus("已完成");
            o3.setSupplierCode("SUP_DENSO_05");
            o3.setCreatedAt(now.minusDays(1));
            inboundOrderMapper.insert(o3);

            InboundDetail d3 = new InboundDetail();
            d3.setInboundId(o3.getId());
            d3.setOrderNo(o3.getOrderNo());
            d3.setMaterialCode("M_PART_006");
            d3.setPackCapacity(12);
            d3.setPlanQty(120);
            d3.setActualQty(120);
            inboundDetailMapper.insert(d3);

            log.info("[初始化] 创建 3 条入库单（2 已完成 + 1 未入库）");
        }
    }

    // ==================== 统计辅助 ====================

    private long countMaterials() { return materialMapper.selectCount(null); }
    private long countSuppliers() { return supplierMapper.selectCount(null); }
    private long countReports() { return aiReportMapper.selectCount(null); }
}
