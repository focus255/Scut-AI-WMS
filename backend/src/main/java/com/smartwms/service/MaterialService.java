/**
 * 物料基础信息服务接口。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.Material;

public interface MaterialService {

    /**
     * 分页查询物料列表。
     */
    Page<Material> page(int current, int size, String keyword);

    /**
     * 按 ID 查询物料。
     */
    Material getById(Long id);

    /**
     * 新增物料。
     */
    void save(Material material);

    /**
     * 更新物料。
     */
    void update(Material material);

    /**
     * 删除物料（先校验是否被单据引用）。
     */
    void delete(Long id);
}
