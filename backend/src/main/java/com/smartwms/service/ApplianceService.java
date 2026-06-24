/**
 * 器具包装参数服务接口。
 *
 * @author Claude
 * @date 2026-06-10
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.Appliance;

public interface ApplianceService {

    /**
     * 分页查询器具列表，支持按物料号搜索。
     */
    Page<Appliance> page(int current, int size, String materialCode);

    /**
     * 按 ID 查询器具详情。
     */
    Appliance getById(Long id);

    /**
     * 新增器具包装配置。
     */
    void save(Appliance appliance);

    /**
     * 更新器具包装配置。
     */
    void update(Appliance appliance);

    /**
     * 删除器具包装配置。
     */
    void delete(Long id);
}
