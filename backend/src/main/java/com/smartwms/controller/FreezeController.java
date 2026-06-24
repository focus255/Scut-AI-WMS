/**
 * 封存解封控制器。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.Result;
import com.smartwms.dto.FreezeRequest;
import com.smartwms.entity.InventoryFreeze;
import com.smartwms.service.FreezeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/freeze")
public class FreezeController {

    private final FreezeService freezeService;

    public FreezeController(FreezeService freezeService) {
        this.freezeService = freezeService;
    }

    /** 封存二维码 */
    @PostMapping("/seal")
    public Result<Void> seal(@Valid @RequestBody FreezeRequest request,
                              @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        freezeService.seal(request, operator);
        return Result.success("封存成功", null);
    }

    /** 解封二维码 */
    @PostMapping("/unseal")
    public Result<Void> unseal(@RequestParam String barcode,
                                @RequestHeader(value = "X-Operator", defaultValue = "admin") String operator) {
        freezeService.unseal(barcode, operator);
        return Result.success("解封成功", null);
    }

    /** 更新封存记录 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @RequestParam String freezeType,
                                @RequestParam String reason) {
        freezeService.update(id, freezeType, reason);
        return Result.success("更新成功", null);
    }

    /** 分页查询封存记录 */
    @GetMapping("/list")
    public Result<Page<InventoryFreeze>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status) {
        return Result.success(freezeService.list(page, size, materialCode, status));
    }
}
