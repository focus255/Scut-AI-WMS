/**
 * 供应商管理控制器，提供供应商的分页查询与基础维护接口。
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.entity.Supplier;
import com.smartwms.service.SupplierService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /**
     * 分页查询供应商列表。
     */
    @GetMapping
    public Result<Page<Supplier>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(supplierService.page(page, size, keyword));
    }

    /**
     * 查询指定供应商详情。
     */
    @GetMapping("/{id}")
    public Result<Supplier> getById(@PathVariable Long id) {
        return Result.success(supplierService.getById(id));
    }

    /**
     * 新增供应商。
     */
    @PostMapping
    public Result<Void> save(@RequestBody Supplier supplier) {
        supplierService.save(supplier);
        return Result.success("供应商创建成功", null);
    }

    /**
     * 更新供应商基础信息。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        supplier.setId(id);
        supplierService.update(supplier);
        return Result.success("供应商更新成功", null);
    }

    /**
     * 删除供应商。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.success("供应商删除成功", null);
    }
}
