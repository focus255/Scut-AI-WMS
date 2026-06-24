/**
 * 应用启动时的默认数据初始化器。
 * 通过 JDBC/MyBatis-Plus 插入覆盖各类前端展示场景的丰富种子数据。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.entity.AiReport;
import com.smartwms.entity.Appliance;
import com.smartwms.entity.Barcode;
import com.smartwms.entity.InboundDetail;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.Material;
import com.smartwms.entity.OutboundDetail;
import com.smartwms.entity.OutboundHistory;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.entity.Permission;
import com.smartwms.entity.Role;
import com.smartwms.entity.RolePermission;
import com.smartwms.entity.Supplier;
import com.smartwms.entity.User;
import com.smartwms.entity.UserRole;
import com.smartwms.entity.InventoryFreeze;
import com.smartwms.mapper.AiReportMapper;
import com.smartwms.mapper.ApplianceMapper;
import com.smartwms.mapper.BarcodeMapper;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.mapper.InventoryFreezeMapper;
import com.smartwms.mapper.InventoryMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundDetailMapper;
import com.smartwms.mapper.OutboundHistoryMapper;
import com.smartwms.mapper.OutboundOrderMapper;
import com.smartwms.mapper.PermissionMapper;
import com.smartwms.mapper.RoleMapper;
import com.smartwms.mapper.RolePermissionMapper;
import com.smartwms.mapper.SupplierMapper;
import com.smartwms.mapper.UserMapper;
import com.smartwms.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 种子数据初始化器（已禁用）。
 * 所有种子数据改为通过 src/main/resources/db/seed_data.sql 脚本导入 MySQL。
 * 如需重新启用，取消下方 @Component 注释并将 @Profile 改为有效环境。
 *
 * @author Focus
 * @date 2026-06-03
 * @deprecated 改用 db/seed_data.sql 脚本作为唯一种子数据源
 */
// @Component
@Profile("_disabled_")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";
    private static final String OPERATOR_USERNAME = "operator";
    private static final String OPERATOR_PASSWORD = "operator123";
    private static final String STATUS_ENABLED = "ENABLED";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final ApplianceMapper applianceMapper;
    private final InventoryMapper inventoryMapper;
    private final AiReportMapper aiReportMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundDetailMapper outboundDetailMapper;
    private final OutboundHistoryMapper outboundHistoryMapper;
    private final BarcodeMapper barcodeMapper;
    private final InventoryFreezeMapper freezeMapper;

    public DataInitializer(UserMapper userMapper,
                           RoleMapper roleMapper,
                           PermissionMapper permissionMapper,
                           UserRoleMapper userRoleMapper,
                           RolePermissionMapper rolePermissionMapper,
                           BCryptPasswordEncoder passwordEncoder,
                           SupplierMapper supplierMapper,
                           MaterialMapper materialMapper,
                           ApplianceMapper applianceMapper,
                           InventoryMapper inventoryMapper,
                           AiReportMapper aiReportMapper,
                           InboundOrderMapper inboundOrderMapper,
                           InboundDetailMapper inboundDetailMapper,
                           OutboundOrderMapper outboundOrderMapper,
                           OutboundDetailMapper outboundDetailMapper,
                           OutboundHistoryMapper outboundHistoryMapper,
                           BarcodeMapper barcodeMapper,
                           InventoryFreezeMapper freezeMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.passwordEncoder = passwordEncoder;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.applianceMapper = applianceMapper;
        this.inventoryMapper = inventoryMapper;
        this.aiReportMapper = aiReportMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundDetailMapper = outboundDetailMapper;
        this.outboundHistoryMapper = outboundHistoryMapper;
        this.barcodeMapper = barcodeMapper;
        this.freezeMapper = freezeMapper;
    }

    @Override
    public void run(String... args) {
        log.info("========== [初始化] 开始装载种子数据 ==========");

        seedRolesAndPermissions();

        Long adminUserId = createUserIfNotExists(DEFAULT_USERNAME, DEFAULT_PASSWORD, "管理员");
        Long operatorUserId = createUserIfNotExists(OPERATOR_USERNAME, OPERATOR_PASSWORD, "操作员");
        assignRoleToUser(adminUserId, "ADMIN");
        assignRoleToUser(operatorUserId, "OPERATOR");

        seedSuppliers();

        // 3. 物料（15 种，超过分页 10 条阈值）
        seedMaterials();

        // 4. 器具包装
        seedAppliances();

        // 5. 库存（覆盖 NORMAL / LOW_STOCK / HIGH / DEAD_STOCK 四种水位）
        seedInventories();

        // 5.5. 为种子库存生成虚拟入库单和二维码，确保所有物料可追溯时间、支持 FIFO
        seedBarcodesForInventory();

        // 5.6. 扩充出库历史流水（120天跨度，支撑日均消耗计算与呆滞检测）
        seedRichOutboundHistory();

        // 5.7. 封存/解封历史记录
        seedFreezeRecords();

        // 6. AI 报告（覆盖 SUCCESS / MOCKED / PENDING + 各风险类型）
        seedAiReports();

        // 7. 入库单（覆盖未入库 / 已完成）
        seedInboundOrders();

        // 8. 出库单（覆盖已完成 / 部分出库 / 未出库）
        seedOutboundOrders();

        log.info("========== [初始化] 种子数据装载完毕（"
                + countMaterials() + " 种物料，"
                + countSuppliers() + " 家供应商，"
                + countReports() + " 份AI报告）==========");
    }

    private void seedRolesAndPermissions() {
        insertPermission("supplier:read", "查看供应商");
        insertPermission("supplier:write", "维护供应商");
        insertPermission("user:read", "查看用户");
        insertPermission("user:write", "维护用户");
        insertPermission("role:read", "查看角色");
        insertPermission("role:write", "维护角色");

        insertRole("ADMIN", "系统管理员", "拥有全部后台权限");
        insertRole("MANAGER", "业务管理员", "具备供应商管理权限");
        insertRole("OPERATOR", "操作员", "仅保留基础只读供应商权限");

        bindRolePermission("ADMIN", "supplier:read");
        bindRolePermission("ADMIN", "supplier:write");
        bindRolePermission("ADMIN", "user:read");
        bindRolePermission("ADMIN", "user:write");
        bindRolePermission("ADMIN", "role:read");
        bindRolePermission("ADMIN", "role:write");
        bindRolePermission("MANAGER", "supplier:read");
        bindRolePermission("MANAGER", "supplier:write");
        bindRolePermission("OPERATOR", "supplier:read");
    }

    private Long createUserIfNotExists(String username, String rawPassword, String nickname) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (existing != null) {
            if (existing.getNickname() == null || existing.getNickname().isBlank() || existing.getStatus() == null) {
                existing.setNickname(nickname);
                existing.setStatus(STATUS_ENABLED);
                userMapper.updateById(existing);
            }
            return existing.getUserId();
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setStatus(STATUS_ENABLED);
        userMapper.insert(user);
        log.info("[初始化] 创建账号: {} / {}", username, rawPassword);
        return user.getUserId();
    }

    private void assignRoleToUser(Long userId, String roleCode) {
        Role role = getRoleByCode(roleCode);
        if (role == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, role.getId()));
        if (count == 0) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }
    }

    private void insertRole(String roleCode, String roleName, String remark) {
        if (getRoleByCode(roleCode) == null) {
            Role role = new Role();
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setRemark(remark);
            roleMapper.insert(role);
        }
    }

    private void insertPermission(String permissionCode, String permissionName) {
        if (getPermissionByCode(permissionCode) == null) {
            Permission permission = new Permission();
            permission.setPermissionCode(permissionCode);
            permission.setPermissionName(permissionName);
            permissionMapper.insert(permission);
        }
    }

    private void bindRolePermission(String roleCode, String permissionCode) {
        Role role = getRoleByCode(roleCode);
        Permission permission = getPermissionByCode(permissionCode);
        if (role == null || permission == null) {
            return;
        }
        Long count = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, role.getId())
                .eq(RolePermission::getPermissionId, permission.getId()));
        if (count == 0) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permission.getId());
            rolePermissionMapper.insert(rolePermission);
        }
    }

    private Role getRoleByCode(String roleCode) {
        return roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode));
    }

    private Permission getPermissionByCode(String permissionCode) {
        return permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermissionCode, permissionCode));
    }

    private void seedSuppliers() {
        insertSupplier("SUP_VWG_09", "一汽大众佛山配件厂", "李经理", "13800000001");
        insertSupplier("SUP_BOSCH_01", "博世汽车部件（苏州）有限公司", "王主管", "13800000002");
        insertSupplier("SUP_CONT_03", "大陆汽车电子（芜湖）有限公司", "陈经理", "13800000003");
        insertSupplier("SUP_DENSO_05", "电装（中国）投资有限公司", "赵经理", "13800000004");
        insertSupplier("SUP_ZF_07", "采埃孚汽车科技（上海）有限公司", "孙经理", "13800000005");
    }

    private void insertSupplier(String code, String name, String contactName, String contactPhone) {
        if (supplierMapper.selectCount(
                new LambdaQueryWrapper<Supplier>().eq(Supplier::getSupplierCode, code)) == 0) {
            Supplier supplier = new Supplier();
            supplier.setSupplierCode(code);
            supplier.setSupplierName(name);
            supplier.setContactName(contactName);
            supplier.setContactPhone(contactPhone);
            supplierMapper.insert(supplier);
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
            Material material = new Material();
            material.setMaterialCode(code);
            material.setMaterialName(name);
            material.setSupplierCode(supplierCode);
            materialMapper.insert(material);
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
            Appliance appliance = new Appliance();
            appliance.setMaterialCode(materialCode);
            appliance.setSupplierCode(supplierCode);
            appliance.setPackType(type);
            appliance.setPackCapacity(cap);
            applianceMapper.insert(appliance);
        }
    }

    // ==================== 库存种子数据（四级评级体系：正常/低储/高储/呆滞） ====================

    /**
     * 评级公式说明：
     *   补货预警线 = (日均销量 × leadTimeDays) + safetyStock
     *   DOHF = stockQty / 日均销量
     *   呆滞判定：最近出库距今 >= 90 天
     *
     * 设计覆盖场景（15 种物料）：
     *   - 正常水位 4 种：M_PART_001, M_PART_002, M_PART_003, M_PART_013
     *   - 低储预警 4 种：M_PART_004, M_PART_005, M_PART_012, M_PART_015
     *   - 高储积压 3 种：M_PART_006, M_PART_007, M_PART_008
     *   - 呆滞物料 3 种：M_PART_009, M_PART_010, M_PART_011
     *   - 正常（高消耗）1 种：M_PART_014
     */
    private void seedInventories() {
        // === 正常水位（5种）—— 库存高于预警线，DOHF在控制范围内 ===
        // M_PART_001: 日均~10件, 预警线=10*5+50=100, DOHF≈200/10=20≤maxDays=25
        insertInventory("M_PART_001", 200, 3, 25, 50, 5);
        // M_PART_002: 日均~9件, 预警线=9*5+45=90, DOHF≈180/9=20≤maxDays=25
        insertInventory("M_PART_002", 180, 3, 25, 45, 5);
        // M_PART_003: 日均~12件, 预警线=12*7+60=144, DOHF≈300/12=25≤maxDays=30
        insertInventory("M_PART_003", 300, 5, 30, 60, 7);
        // M_PART_013: 日均~6件, 预警线=6*7+30=72, DOHF≈96/6=16≤maxDays=20
        insertInventory("M_PART_013", 96, 5, 20, 30, 7);
        // M_PART_014: 日均~16件, 预警线=16*7+60=172, DOHF≈400/16=25≤maxDays=30
        insertInventory("M_PART_014", 400, 3, 30, 60, 7);

        // === 低储预警（4种）—— 库存跌破补货预警线 ===
        // M_PART_004: 日均10件, 预警线=10*7+50=120, 库存50 < 120 → 低储
        insertInventory("M_PART_004", 50, 7, 30, 50, 7);
        // M_PART_005: 日均10件, 预警线=10*5+50=100, 库存30 < 100 → 严重低储
        insertInventory("M_PART_005", 30, 5, 15, 50, 5);
        // M_PART_012: 日均8件, 预警线=8*5+40=80, 库存20 < 80 → 严重低储
        insertInventory("M_PART_012", 20, 3, 15, 40, 5);
        // M_PART_015: 日均10件, 预警线=10*5+50=100, 库存35 < 100 → 严重低储
        insertInventory("M_PART_015", 35, 5, 15, 50, 5);

        // === 高储积压（3种）—— DOHF远超maxStockDays控制上限 ===
        // M_PART_006: 日均4件, DOHF=200/4=50 > maxDays=15 → 高储
        insertInventory("M_PART_006", 200, 5, 15, 30, 7);
        // M_PART_007: 日均3件, DOHF=160/3≈53 > maxDays=15 → 高储
        insertInventory("M_PART_007", 160, 3, 15, 25, 7);
        // M_PART_008: 日均8件, DOHF=510/8≈64 > maxDays=15 → 高储
        insertInventory("M_PART_008", 510, 3, 15, 40, 5);

        // === 呆滞物料（3种）—— 超过90天无出库记录（由出库历史数据控制） ===
        // M_PART_009: 库存80件, 最后出库120天前 → 呆滞
        insertInventory("M_PART_009", 80, 7, 20, 30, 7);
        // M_PART_010: 库存120件, 最后出库100天前 → 呆滞
        insertInventory("M_PART_010", 120, 5, 15, 20, 7);
        // M_PART_011: 库存240件, 最后出库95天前 → 呆滞
        insertInventory("M_PART_011", 240, 3, 20, 35, 5);

        // === 正常（高消耗大流量物料，1种） ===
        // M_PART_014: 日均16件, 预警线=16*7+60=172, DOHF=400/16=25≤maxDays=25
        insertInventory("M_PART_014", 400, 3, 25, 60, 7);
    }

    /**
     * 插入库存记录（含安全库存与补货提前期参数）。
     *
     * @param code         物料号
     * @param qty          当前库存量
     * @param minDays      低储控制天数（用于前端参考显示）
     * @param maxDays      高储控制天数（DOHF上限）
     * @param safetyStock  安全库存量（件）
     * @param leadTimeDays 补货提前期（天）
     */
    private void insertInventory(String code, int qty, int minDays, int maxDays,
                                  int safetyStock, int leadTimeDays) {
        if (inventoryMapper.selectCount(
                new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, code)) == 0) {
            Inventory inventory = new Inventory();
            inventory.setMaterialCode(code);
            inventory.setStockQty(qty);
            inventory.setMinStockDays(minDays);
            inventory.setMaxStockDays(maxDays);
            inventory.setSafetyStock(safetyStock);
            inventory.setLeadTimeDays(leadTimeDays);
            inventoryMapper.insert(inventory);
        }
    }

    // ==================== 种子库存 → 虚拟入库单 + 二维码（FIFO 时间追溯） ====================

    /**
     * 为种子库存的每种物料生成一条虚拟入库单和对应二维码。
     * 入库时间按物料序号错开（每往前一天一批），同一入库单内各箱时间逐毫秒递增。
     * 确保所有库存物料都有可追溯的时间戳，满足 FIFO 先进先出排序。
     */
    private void seedBarcodesForInventory() {
        // 跳过已有二维码的物料（避免重复初始化）
        if (barcodeMapper.selectCount(null) > 0) {
            log.info("[初始化] 二维码表已有数据，跳过种子二维码生成");
            return;
        }

        LocalDateTime baseTime = LocalDateTime.now().minusDays(30); // 最早一批 30 天前入库
        java.util.List<Inventory> inventories = inventoryMapper.selectList(null);

        for (int i = 0; i < inventories.size(); i++) {
            Inventory inv = inventories.get(i);
            String materialCode = inv.getMaterialCode();
            int stockQty = inv.getStockQty() != null ? inv.getStockQty() : 0;
            if (stockQty <= 0) continue;

            // 查找物料对应的供应商
            Material material = materialMapper.selectOne(
                    new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, materialCode)
            );
            String supplierCode = material != null ? material.getSupplierCode() : "SUP_VWG_09";

            // 查找器具包装容量（默认 20）
            int packCapacity = 20;
            Appliance appliance = applianceMapper.selectOne(
                    new LambdaQueryWrapper<Appliance>()
                            .eq(Appliance::getMaterialCode, materialCode)
                            .eq(Appliance::getSupplierCode, supplierCode)
            );
            if (appliance != null && appliance.getPackCapacity() != null && appliance.getPackCapacity() > 0) {
                packCapacity = appliance.getPackCapacity();
            }

            // 创建虚拟入库单（每条物料错开 2 天，确保 FIFO 顺序明显）
            LocalDateTime orderTime = baseTime.plusDays(i * 2L);
            String orderNo = String.format("RK%s%04d",
                    orderTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")), i + 1);
            InboundOrder order = new InboundOrder();
            order.setOrderNo(orderNo);
            order.setSupplierCode(supplierCode);
            order.setStatus("已完成");
            // MyBatis-Plus 自动填充会覆盖 createdAt，需要绕过
            inboundOrderMapper.insert(order);
            // 手动更新 createdAt 为指定时间
            InboundOrder updateOrder = new InboundOrder();
            updateOrder.setId(order.getId());
            updateOrder.setCreatedAt(orderTime);
            inboundOrderMapper.updateById(updateOrder);
            order.setCreatedAt(orderTime);

            // 创建入库明细
            InboundDetail detail = new InboundDetail();
            detail.setInboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(materialCode);
            detail.setPackCapacity(packCapacity);
            detail.setPlanQty(stockQty);
            detail.setActualQty(stockQty);
            inboundDetailMapper.insert(detail);

            // 生成二维码（每箱一个，整箱模式：每箱 remainingQty = packCapacity）
            int boxCount = stockQty / packCapacity; // 整箱模式确保整除
            for (int boxSeq = 1; boxSeq <= boxCount; boxSeq++) {
                String barcode = String.format("WMS|%s|%s|%d|%d|%d|%d",
                        materialCode, supplierCode, stockQty, packCapacity, packCapacity, boxSeq);

                Barcode bc = new Barcode();
                bc.setMaterialCode(materialCode);
                bc.setSupplierCode(supplierCode);
                bc.setBarcode(barcode);
                bc.setInboundId(order.getId());
                bc.setType("inbound");
                bc.setStatus("在库");
                bc.setRemainingQty(packCapacity); // 整箱
                barcodeMapper.insert(bc);

                // 每箱错开 1 毫秒，确保同物料内 FIFO 可区分
                LocalDateTime barcodeTime = orderTime.plusNanos(boxSeq * 1000000L);
                Barcode updateBc = new Barcode();
                updateBc.setId(bc.getId());
                updateBc.setCreatedAt(barcodeTime);
                barcodeMapper.updateById(updateBc);
            }
            log.info("[初始化] 物料 {} 生成 {} 箱种子二维码（入库时间: {}）", materialCode, boxCount, orderTime);
        }
    }

    // ==================== 扩充出库历史流水（120天跨度） ====================

    /**
     * 为所有物料生成丰富的出库历史记录，覆盖120天跨度。
     * 设计原则：
     *   - 活跃物料：在近30天内有持续出库记录，支撑日均消耗计算
     *   - 呆滞物料：最后出库记录在90天以上之前
     *   - 每个出库事件创建完整的订单→明细→流水链路
     */
    private void seedRichOutboundHistory() {
        // 幂等检查：如果已有出库历史（SQL脚本或上次启动已写入）则跳过
        long historyCount = outboundHistoryMapper.selectCount(null);
        if (historyCount > 0) {
            log.info("[初始化] 出库历史已有 {} 条记录，跳过生成", historyCount);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // ===== 场景定义：每种物料的出库参数 =====
        // { materialCode, packCapacity, avgDailyUse(近30天), stopDaysAgo(0=活跃至今), totalSpanDays }
        Object[][] schedules = {
            // === 正常水位（4种）—— 持续出库，日均消耗适中 ===
            {"M_PART_001", 20, 10, 3, 120},   // 日均10件，最后出库3天前
            {"M_PART_002", 20, 9, 3, 120},    // 日均9件
            {"M_PART_003", 50, 12, 2, 120},   // 日均12件
            {"M_PART_013", 16, 6, 4, 120},    // 日均6件

            // === 低储预警（4种）—— 高消耗但库存不足 ===
            {"M_PART_004", 10, 10, 2, 120},   // 日均10件，库存仅50
            {"M_PART_005", 40, 10, 2, 120},   // 日均10件，库存仅30
            {"M_PART_012", 25, 8, 5, 120},    // 日均8件，库存仅20
            {"M_PART_015", 40, 10, 2, 120},   // 日均10件，库存仅35

            // === 高储积压（3种）—— 低消耗但库存极高 ===
            {"M_PART_006", 12, 4, 3, 120},    // 日均4件，库存200
            {"M_PART_007", 8, 3, 3, 120},     // 日均3件，库存160
            {"M_PART_008", 15, 8, 3, 120},    // 日均8件，库存510

            // === 呆滞物料（3种）—— 出库记录在90天前中断 ===
            {"M_PART_009", 60, 3, 120, 120},  // 最后出库120天前
            {"M_PART_010", 10, 2, 100, 120},  // 最后出库100天前
            {"M_PART_011", 30, 5, 95, 120},   // 最后出库95天前

            // === 正常（高消耗大流量，1种） ===
            {"M_PART_014", 50, 16, 2, 120},   // 日均16件，大流量
        };

        int totalRecords = 0;
        for (Object[] s : schedules) {
            String matCode = (String) s[0];
            int packCap = (int) s[1];
            int dailyUse = (int) s[2];
            int stopDays = (int) s[3];
            int spanDays = (int) s[4];

            int created = generateMaterialOutboundHistory(matCode, packCap, dailyUse,
                    stopDays, spanDays, now);
            totalRecords += created;
        }

        log.info("[初始化] 生成 {} 条出库历史记录（覆盖 {} 种物料）", totalRecords, schedules.length);
    }

    /**
     * 为单个物料生成一系列出库历史记录。
     *
     * @param materialCode 物料号
     * @param packCapacity 包装容量
     * @param avgDailyUse  近30天目标日均消耗（件/天）
     * @param stopDaysAgo  最后一条出库距今天数（呆滞物料设为大值）
     * @param spanDays     出库历史总跨度（天）
     * @param now          当前时间基准
     * @return 生成的记录数
     */
    private int generateMaterialOutboundHistory(String materialCode, int packCapacity,
                                                 int avgDailyUse, int stopDaysAgo,
                                                 int spanDays, LocalDateTime now) {
        // 查找物料对应的供应商
        Material material = materialMapper.selectOne(
                new LambdaQueryWrapper<Material>().eq(Material::getMaterialCode, materialCode)
        );
        String supplierCode = material != null ? material.getSupplierCode() : "SUP_VWG_09";

        int count = 0;
        // 间隔天数：根据日均消耗自适应，消耗越低间隔越大
        int intervalDays = Math.max(2, Math.min(7, 30 / Math.max(1, avgDailyUse)));
        // 每次出库量 = 日均消耗 × 间隔天数（直接使用精确值，不受整箱限制）
        int qtyPerOutbound = Math.max(1, avgDailyUse * intervalDays);

        // 日期游标：从 spanDays 天前开始向现在推进
        LocalDateTime cursor = now.minusDays(spanDays);
        // 停止日期：stopDaysAgo 天前
        LocalDateTime stopDate = now.minusDays(stopDaysAgo);

        int seq = 0;
        while (cursor.isBefore(stopDate) || cursor.isEqual(stopDate)) {
            seq++;
            String orderNo = String.format("CK%s%s%04d",
                    materialCode.replace("_", "").substring(Math.max(0, materialCode.length() - 5)),
                    cursor.format(DateTimeFormatter.ofPattern("MMddHHmm")), seq);

            // 创建出库单
            OutboundOrder order = new OutboundOrder();
            order.setOrderNo(orderNo);
            order.setStatus("已完成");
            order.setCreatedAt(cursor);
            outboundOrderMapper.insert(order);

            // 创建出库明细
            OutboundDetail detail = new OutboundDetail();
            detail.setOutboundId(order.getId());
            detail.setOrderNo(orderNo);
            detail.setMaterialCode(materialCode);
            detail.setPackCapacity(packCapacity);
            detail.setPlanQty(qtyPerOutbound);
            detail.setActualQty(qtyPerOutbound);
            outboundDetailMapper.insert(detail);

            // 创建出库流水（关联虚拟来源信息）
            OutboundHistory history = new OutboundHistory();
            history.setOutboundId(order.getId());
            history.setOutboundOrderNo(orderNo);
            history.setOutboundDetailId(detail.getId());
            history.setMaterialCode(materialCode);
            history.setInboundId(0L);
            history.setInboundOrderNo("种子数据");
            history.setInboundDetailId(0L);
            history.setBarcodeId(0L);
            history.setBarcode(materialCode + "-SEED-" + seq);
            history.setDeductQty(qtyPerOutbound);
            history.setCreatedAt(cursor);
            outboundHistoryMapper.insert(history);

            count++;
            cursor = cursor.plusDays(intervalDays);
        }

        return count;
    }

    // ==================== 封存/解封历史记录 ====================

    /**
     * 生成封存与解封的历史记录，丰富统计数据维度。
     * 覆盖场景：质量封存、管理封存、已解封。
     */
    private void seedFreezeRecords() {
        // 检查是否已有封存记录
        if (freezeMapper.selectCount(null) > 0) {
            log.info("[初始化] 封存记录已存在，跳过");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 为呆滞物料 M_PART_009 封存一批（质量原因，仍未解封）
        insertFreeze("M_PART_009", "WMS|M_PART_009|SUP_DENSO_05|80|60|60|1",
                "QUALITY", "氧传感器批次外观缺陷，待供应商确认退货", "admin",
                now.minusDays(60), null, "FROZEN");

        // 为 M_PART_010 封存后解封（管理封存→已解封，完整生命周期）
        insertFreeze("M_PART_010", "WMS|M_PART_010|SUP_ZF_07|120|10|10|1",
                "ADMIN", "TCU模块年度盘点临时封存，盘点完成后解封", "admin",
                now.minusDays(45), now.minusDays(15), "UNFROZEN");

        // 为高储物料 M_PART_008 部分封存（质量争议，待复检）
        insertFreeze("M_PART_008", "WMS|M_PART_008|SUP_CONT_03|510|15|15|10",
                "QUALITY", "燃油泵模块批次抽检不合格，封存待第三方复检", "admin",
                now.minusDays(30), null, "FROZEN");

        // 为低储物料 M_PART_005 封存（质量问题导致可用库存更紧张）
        insertFreeze("M_PART_005", "WMS|M_PART_005|SUP_BOSCH_01|30|40|40|1",
                "QUALITY", "刹车片组件摩擦系数抽检异常，紧急封存排查", "admin",
                now.minusDays(10), null, "FROZEN");

        log.info("[初始化] 创建 4 条封存/解封记录");
    }

    /**
     * 插入一条封存记录。
     */
    private void insertFreeze(String materialCode, String barcode,
                               String freezeType, String reason, String operator,
                               LocalDateTime freezeTime, LocalDateTime unfreezeTime,
                               String status) {
        InventoryFreeze freeze = new InventoryFreeze();
        freeze.setBarcodeId(0L); // 种子数据使用虚拟关联
        freeze.setMaterialCode(materialCode);
        freeze.setBarcode(barcode);
        freeze.setFreezeType(freezeType);
        freeze.setReason(reason);
        freeze.setOperator(operator);
        freeze.setFreezeTime(freezeTime);
        freeze.setUnfreezeTime(unfreezeTime);
        freeze.setStatus(status);
        freezeMapper.insert(freeze);
    }

    // ==================== AI 报告（覆盖全部状态和风险类型） ====================

    private void seedAiReports() {
        // 幂等检查：如果已有AI报告则跳过
        if (aiReportMapper.selectCount(null) > 0) {
            log.info("[初始化] AI报告已存在，跳过生成");
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. 高储物料 M_PART_008 → AI 诊断为滞销风险（库存510件，DOHF≈64天，上限15天）
        insertAiReport("M_PART_008", 510, "DEAD_STOCK", "MEDIUM", "SUCCESS", 0, 0.88f,
                "燃油泵模块当前库存高达510件（34个整箱），DOHF约64天，远超控制上限15天。" +
                "近30日均消耗仅8件/天，现有库存可维持64天，资金占用严重。" +
                "结合未来需求预测，短期内无大规模消耗计划，存在明确的呆滞积压风险。",
                "建议立即暂停该物料采购计划，优先消耗现有库存。可考虑与供应商协商退货或调拨至其他工厂。" +
                "建议将maxStockDays收紧至10天以内，建立库存消化跟踪机制。",
                now.minusHours(2));

        // 2. 严重低储 M_PART_005 → AI 建议紧急补货（库存30件，预警线100件）
        insertAiReport("M_PART_005", 30, "LOW_STOCK", "CRITICAL", "SUCCESS", 200, 0.95f,
                "刹车片组件当前库存仅30件，已严重跌破补货预警线（10件/天×5天+50件=100件）。" +
                "按当前日均消耗10件计算，库存仅可维持3天，面临总装线停产风险。属于最高优先级断供预警。" +
                "此外该物料还有1箱因质量问题被封存（10天前），进一步加剧了可用库存紧张。",
                "建议立即向供应商SUP_BOSCH_01发起紧急补货订单。推荐补货量200件（标准铁箱5箱），" +
                "可将库存恢复至安全水位。建议同步评估安全库存参数是否需从50件上调。",
                now.minusHours(3));

        // 3. ESP模块 M_PART_012 → AI 双重风险（极端低储+潜在呆滞，库存20件，预警线80件）
        insertAiReport("M_PART_012", 20, "BOTH", "CRITICAL", "SUCCESS", 150, 0.91f,
                "ESP车身稳定模块库存仅剩20件，已触发极端低储警报（预警线=8件/天×5天+40件=80件）。" +
                "按日均消耗8件计算，库存仅可维持2.5天。同时该物料近5天无出库记录，需关注需求波动风险。",
                "建议紧急补货150件至基础安全线以上。同时与计划部门确认未来两周排产计划，" +
                "若需求暂停则标记为潜在呆滞观察对象，避免补货后形成新积压。",
                now.minusHours(1));

        // 4. Mock 兜底报告 — M_PART_004 低储（库存50件，预警线120件）
        insertAiReport("M_PART_004", 50, "LOW_STOCK", "HIGH", "MOCKED", 120, 0.6f,
                "[降级引擎Mock提示]: 由于外部AI推演大模型服务连线超时，系统自动执行基本精益规则扫描。" +
                "发动机控制单元ECU当前库存50件，已跌破补货预警线（10件/天×7天+50件=120件）。" +
                "库存仅可维持约5天，存在断供风险。",
                "建议向供应商发起紧急补货。推荐补货量：120件，可将库存恢复至预警线以上。",
                now.minusMinutes(30));

        // 5. 正常物料 M_PART_001 → AI 确认无风险（库存200件，预警线100件，DOHF=20天）
        insertAiReport("M_PART_001", 200, "NORMAL", "LOW", "SUCCESS", 0, 0.97f,
                "左前大灯总成当前库存200件（10个整箱），处于安全水位区间。" +
                "补货预警线100件（10件/天×5天+50件），实际库存200件，安全边际充足。" +
                "近30日出库稳定，日均消耗约10件，DOHF约20天（上限20天），库存健康度良好，无需干预。",
                "维持当前采购节奏，按周例行检查库存水位即可。建议关注DOHF趋势，若持续逼近上限可适当调整。",
                now.minusDays(1));

        // 6. PENDING 状态报告 — M_PART_015（模拟正在等待 AI 分析）
        insertAiReport("M_PART_015", 35, "NORMAL", "LOW", "PENDING", 0, 0f,
                "分析中...", "分析中...", now.minusMinutes(5));

        // 7. 呆滞物料 M_PART_009 → AI 诊断为呆滞风险（闲置120天，库存80件）
        insertAiReport("M_PART_009", 80, "DEAD_STOCK", "HIGH", "SUCCESS", 0, 0.85f,
                "氧传感器当前库存80件，已连续120天无出库记录，远超90天呆滞判定阈值。" +
                "该物料属于上一代发动机平台专用件，随着车型换代需求急剧萎缩。" +
                "当前库存按历史日均3件计算，DOHF约27天，但由于无实际需求，存在过期报废风险。",
                "建议与研发确认该物料是否还有后续车型使用计划。若无，则启动报废或折价处理流程。" +
                "若有少量需求，建议保留最小安全库存（10件），其余协商退货。",
                now.minusDays(2));

        // 8. 呆滞物料 M_PART_011 → AI 诊断（闲置95天，库存240件）
        insertAiReport("M_PART_011", 240, "DEAD_STOCK", "MEDIUM", "MOCKED", 0, 0.6f,
                "[降级引擎Mock提示]: 雨刮电机当前库存240件，已连续95天无出库记录，判定为呆滞物料。" +
                "建议暂停采购并优先消耗现有库存。",
                "建议暂缓采购，启动库存消化计划。可考虑与供应商协商调拨或退货。",
                now.minusDays(1));
    }

    private void insertAiReport(String materialCode, int stock, String riskType,
                                String riskLevel, String status, int suggestedQty,
                                float confidence, String analysis, String suggestion,
                                LocalDateTime createdAt) {
        AiReport report = new AiReport();
        report.setMaterialCode(materialCode);
        report.setCurrentStock(stock);
        report.setRiskType(riskType);
        report.setRiskLevel(riskLevel);
        report.setPredictionStatus(status);
        report.setSuggestedQty(suggestedQty);
        report.setConfidence(confidence);
        report.setAnalysisContent(analysis);
        report.setReplenishmentSuggestion(suggestion);
        report.setCreatedAt(createdAt);
        report.setUpdatedAt(createdAt);
        aiReportMapper.insert(report);
    }

    // ==================== 入库单（覆盖未入库/已完成状态） ====================

    private void seedInboundOrders() {
        LocalDateTime now = LocalDateTime.now();
        String today = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 已完成入库单
        if (inboundOrderMapper.selectCount(null) == 0) {
            InboundOrder order1 = new InboundOrder();
            order1.setOrderNo("RK" + today + "S001");
            order1.setStatus("已完成");
            order1.setSupplierCode("SUP_VWG_09");
            order1.setCreatedAt(now.minusHours(5));
            inboundOrderMapper.insert(order1);

            InboundDetail detail1 = new InboundDetail();
            detail1.setInboundId(order1.getId());
            detail1.setOrderNo(order1.getOrderNo());
            detail1.setMaterialCode("M_PART_001");
            detail1.setPackCapacity(20);
            detail1.setPlanQty(200);
            detail1.setActualQty(200);
            inboundDetailMapper.insert(detail1);
            // 生成二维码（已完成 → "在库"）
            seedBarcodes(order1, detail1, "在库");

            InboundOrder order2 = new InboundOrder();
            order2.setOrderNo("RK" + today + "S002");
            order2.setStatus("未入库");
            order2.setSupplierCode("SUP_BOSCH_01");
            order2.setCreatedAt(now.minusHours(1));
            inboundOrderMapper.insert(order2);

            InboundDetail detail2 = new InboundDetail();
            detail2.setInboundId(order2.getId());
            detail2.setOrderNo(order2.getOrderNo());
            detail2.setMaterialCode("M_PART_003");
            detail2.setPackCapacity(50);
            detail2.setPlanQty(150);
            detail2.setActualQty(0);
            inboundDetailMapper.insert(detail2);
            // 生成二维码（未入库 → "待入库"）
            seedBarcodes(order2, detail2, "待入库");

            InboundOrder order3 = new InboundOrder();
            order3.setOrderNo("RK" + today + "S003");
            order3.setStatus("已完成");
            order3.setSupplierCode("SUP_DENSO_05");
            order3.setCreatedAt(now.minusDays(1));
            inboundOrderMapper.insert(order3);

            InboundDetail detail3 = new InboundDetail();
            detail3.setInboundId(order3.getId());
            detail3.setOrderNo(order3.getOrderNo());
            detail3.setMaterialCode("M_PART_006");
            detail3.setPackCapacity(12);
            detail3.setPlanQty(120);
            detail3.setActualQty(120);
            inboundDetailMapper.insert(detail3);
            // 生成二维码（已完成 → "在库"）
            seedBarcodes(order3, detail3, "在库");

            log.info("[初始化] 创建 3 条入库单（2 已完成 + 1 未入库），含二维码");
        }
    }

    /**
     * 为一条入库明细生成二维码记录（整箱模式）。
     */
    private void seedBarcodes(InboundOrder order, InboundDetail detail, String status) {
        int boxCount = detail.getPlanQty() / detail.getPackCapacity();
        for (int i = 0; i < boxCount; i++) {
            Barcode bc = new Barcode();
            bc.setMaterialCode(detail.getMaterialCode());
            bc.setSupplierCode(order.getSupplierCode());
            // 格式: WMS|物料|供应商|计划数|箱容量|实收数|箱号
            bc.setBarcode(String.format("WMS|%s|%s|%d|%d|%d|%d",
                    detail.getMaterialCode(),
                    order.getSupplierCode(),
                    detail.getPlanQty(),
                    detail.getPackCapacity(),
                    detail.getPackCapacity(),
                    i + 1));
            bc.setStatus(status);
            bc.setInboundId(order.getId());
            bc.setRemainingQty(detail.getPackCapacity()); // 整箱
            barcodeMapper.insert(bc);
        }
    }

    // ==================== 出库单种子数据（含关联看板） ====================

    private void seedOutboundOrders() {
        if (outboundOrderMapper.selectCount(null) > 0) return;

        LocalDateTime now = LocalDateTime.now();

        // --- 已完成出库单 M_PART_001（200件=10箱），扣减对应入库二维码 ---
        OutboundOrder o1 = createOutboundOrder("CK" + now.minusDays(5).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                "已完成", now.minusDays(5), "M_PART_001", 20, 200, 200, 10);

        // --- 已完成出库单 M_PART_003（150件=3箱） ---
        OutboundOrder o2 = createOutboundOrder("CK" + now.minusDays(3).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                "已完成", now.minusDays(3), "M_PART_003", 50, 150, 150, 3);

        // --- 已完成出库单 M_PART_006（60件=5箱） ---
        OutboundOrder o3 = createOutboundOrder("CK" + now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                "已完成", now.minusDays(1), "M_PART_006", 12, 60, 60, 5);

        // --- 部分出库单 M_PART_010（30件=3箱，计划50件=5箱） ---
        OutboundOrder o4 = createOutboundOrder("CK" + now.minusHours(12).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                "部分出库", now.minusHours(12), "M_PART_010", 10, 50, 30, 3);

        // --- 未出库单 M_PART_011（计划60件=2箱，拣货2箱但未确认） ---
        OutboundOrder o5 = createOutboundOrder("CK" + now.minusHours(2).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                "未出库", now.minusHours(2), "M_PART_011", 30, 60, 0, 2);

        log.info("[初始化] 创建 5 条出库单（3 已完成 + 1 部分出库 + 1 未出库），含关联看板");
    }

    /**
     * 创建一条出库单，关联真实入库二维码。
     * @param pickBoxes 拣货箱数（<= 该物料在库整箱数）
     */
    private OutboundOrder createOutboundOrder(String orderNo, String status, LocalDateTime createdAt,
                                               String materialCode, int packCapacity, int planQty, int actualQty, int pickBoxes) {
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(orderNo);
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        outboundOrderMapper.insert(order);

        OutboundDetail detail = new OutboundDetail();
        detail.setOutboundId(order.getId());
        detail.setOrderNo(orderNo);
        detail.setMaterialCode(materialCode);
        detail.setPackCapacity(packCapacity);
        detail.setPlanQty(planQty);
        detail.setActualQty(actualQty);
        outboundDetailMapper.insert(detail);

        // 查找该物料的在库二维码，按 FIFO 选取 pickBoxes 个
        List<Barcode> barcodes = barcodeMapper.selectList(
                new LambdaQueryWrapper<Barcode>()
                        .eq(Barcode::getType, "inbound")
                        .eq(Barcode::getMaterialCode, materialCode)
                        .eq(Barcode::getStatus, "在库")
                        .orderByAsc(Barcode::getCreatedAt)
        );

        int picked = 0;
        for (Barcode bc : barcodes) {
            if (picked >= pickBoxes) break;
            String newStatus = "已完成".equals(status) || "部分出库".equals(status) ? "已出库" : "待出库";
            bc.setStatus(newStatus);
            if ("已出库".equals(newStatus)) bc.setRemainingQty(0);
            barcodeMapper.updateById(bc);

            // 查找来源入库单信息
            InboundOrder srcOrder = inboundOrderMapper.selectById(bc.getInboundId());
            InboundDetail srcDetail = inboundDetailMapper.selectOne(
                    new LambdaQueryWrapper<InboundDetail>()
                            .eq(InboundDetail::getInboundId, bc.getInboundId())
                            .eq(InboundDetail::getMaterialCode, materialCode)
            );

            OutboundHistory history = new OutboundHistory();
            history.setOutboundId(order.getId());
            history.setOutboundOrderNo(orderNo);
            history.setOutboundDetailId(detail.getId());
            history.setMaterialCode(materialCode);
            history.setInboundId(bc.getInboundId() != null ? bc.getInboundId() : 0L);
            history.setInboundOrderNo(srcOrder != null ? srcOrder.getOrderNo() : "—");
            history.setInboundDetailId(srcDetail != null ? srcDetail.getId() : 0L);
            history.setBarcodeId(bc.getId());
            history.setBarcode(bc.getBarcode());
            history.setDeductQty(packCapacity);
            history.setCreatedAt(createdAt);
            outboundHistoryMapper.insert(history);
            picked++;
        }

        // 扣减库存（已完成/部分出库）
        if (actualQty > 0) {
            Inventory inv = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>().eq(Inventory::getMaterialCode, materialCode)
            );
            if (inv != null) {
                inv.setStockQty(Math.max(0, (inv.getStockQty() != null ? inv.getStockQty() : 0) - actualQty));
                inventoryMapper.updateById(inv);
            }
        }

        return order;
    }

    private long countMaterials() { return materialMapper.selectCount(null); }
    private long countSuppliers() { return supplierMapper.selectCount(null); }
    private long countReports() { return aiReportMapper.selectCount(null); }
}
