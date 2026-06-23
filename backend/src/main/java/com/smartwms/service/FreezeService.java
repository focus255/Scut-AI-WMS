/**
 * 封存解封服务接口。
 *
 * @author Focus
 * @date 2026-06-23
 */
package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.dto.FreezeRequest;
import com.smartwms.entity.InventoryFreeze;

public interface FreezeService {

    /** 封存条码 */
    void seal(FreezeRequest request, String operator);

    /** 解封条码 */
    void unseal(String barcode, String operator);

    /** 分页查询封存记录 */
    Page<InventoryFreeze> list(int page, int size, String materialCode, String status);
}
