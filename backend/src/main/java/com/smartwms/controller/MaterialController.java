/**
 * 物料基础信息控制器。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.entity.Material;
import com.smartwms.service.MaterialService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /**
     * 分页查询物料列表。
     * GET /api/materials?page=1&size=10&keyword=
     */
    @GetMapping
    public Result<Page<Material>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(materialService.page(page, size, keyword));
    }

    /**
     * 按 ID 查询物料详情。
     * GET /api/materials/{id}
     */
    @GetMapping("/{id}")
    public Result<Material> getById(@PathVariable Long id) {
        return Result.success(materialService.getById(id));
    }

    /**
     * 新增物料。
     * POST /api/materials
     */
    @PostMapping
    public Result<Void> save(@RequestBody Material material) {
        materialService.save(material);
        return Result.success("物料创建成功", null);
    }

    /**
     * 更新物料。
     * PUT /api/materials/{id}
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Material material) {
        material.setId(id);
        materialService.update(material);
        return Result.success("物料更新成功", null);
    }

    /**
     * 删除物料。
     * DELETE /api/materials/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.success("物料删除成功", null);
    }
}
