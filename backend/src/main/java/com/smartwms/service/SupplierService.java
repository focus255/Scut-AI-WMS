package com.smartwms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.entity.Supplier;

public interface SupplierService {

    Page<Supplier> page(int current, int size, String keyword);

    Supplier getById(Long id);

    void save(Supplier supplier);

    void update(Supplier supplier);

    void delete(Long id);
}
