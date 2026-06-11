/**
 * 器具包装参数控制器，提供器具的分页查询与基础维护接口。
 *
 * @author Claude
 * @date 2026-06-10
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.entity.Appliance;
import com.smartwms.service.ApplianceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appliances")
public class ApplianceController {

    private final ApplianceService applianceService;

    public ApplianceController(ApplianceService applianceService) {
        this.applianceService = applianceService;
    }

    /**
     * 分页查询器具列表。
     * GET /api/appliances?page=1&size=10&keyword=xxx
     */
    @GetMapping
    public Result<Page<Appliance>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(applianceService.page(page, size, keyword));
    }

    /**
     * 查询指定器具详情。
     */
    @GetMapping("/{id}")
    public Result<Appliance> getById(@PathVariable Long id) {
        return Result.success(applianceService.getById(id));
    }

    /**
     * 新增器具包装配置。
     */
    @PostMapping
    public Result<Void> save(@RequestBody Appliance appliance) {
        applianceService.save(appliance);
        return Result.success("器具创建成功", null);
    }

    /**
     * 更新器具包装配置。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Appliance appliance) {
        appliance.setId(id);
        applianceService.update(appliance);
        return Result.success("器具更新成功", null);
    }

    /**
     * 删除器具包装配置。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        applianceService.delete(id);
        return Result.success("器具删除成功", null);
    }
}
