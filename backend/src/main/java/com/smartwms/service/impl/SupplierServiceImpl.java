package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.Supplier;
import com.smartwms.mapper.ApplianceMapper;
import com.smartwms.mapper.InboundOrderMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.SupplierMapper;
import com.smartwms.service.SupplierService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SupplierServiceImpl implements SupplierService {

    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final ApplianceMapper applianceMapper;
    private final InboundOrderMapper inboundOrderMapper;

    public SupplierServiceImpl(SupplierMapper supplierMapper,
                               MaterialMapper materialMapper,
                               ApplianceMapper applianceMapper,
                               InboundOrderMapper inboundOrderMapper) {
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.applianceMapper = applianceMapper;
        this.inboundOrderMapper = inboundOrderMapper;
    }

    /**
     * 按关键字分页查询供应商，支持编码、名称、联系人和手机号模糊搜索。
     */
    @Override
    public Page<Supplier> page(int current, int size, String keyword) {
        Page<Supplier> page = new Page<>(current, size);
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Supplier::getSupplierCode, keyword)
                    .or()
                    .like(Supplier::getSupplierName, keyword)
                    .or()
                    .like(Supplier::getContactName, keyword)
                    .or()
                    .like(Supplier::getContactPhone, keyword);
        }
        wrapper.orderByDesc(Supplier::getCreatedAt);
        return supplierMapper.selectPage(page, wrapper);
    }

    /**
     * 根据主键查询供应商详情。
     */
    @Override
    public Supplier getById(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        return supplier;
    }

    /**
     * 新增供应商，并校验必填字段与编码唯一性。
     */
    @Override
    public void save(Supplier supplier) {
        validateRequiredFields(supplier);
        validateUniqueCode(supplier.getSupplierCode(), null);
        supplierMapper.insert(supplier);
    }

    /**
     * 更新供应商前先校验记录是否存在以及供应商编码是否冲突。
     */
    @Override
    public void update(Supplier supplier) {
        validateRequiredFields(supplier);
        Supplier existing = supplierMapper.selectById(supplier.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }
        validateUniqueCode(supplier.getSupplierCode(), supplier.getId());
        supplierMapper.updateById(supplier);
    }

    /**
     * 删除供应商前校验是否仍被物料、包装方案或入库单引用。
     */
    @Override
    public void delete(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在");
        }

        String supplierCode = supplier.getSupplierCode();
        long materialRefs = materialMapper.selectCount(
                new LambdaQueryWrapper<com.smartwms.entity.Material>()
                        .eq(com.smartwms.entity.Material::getSupplierCode, supplierCode)
        );
        long applianceRefs = applianceMapper.selectCount(
                new LambdaQueryWrapper<com.smartwms.entity.Appliance>()
                        .eq(com.smartwms.entity.Appliance::getSupplierCode, supplierCode)
        );
        long inboundRefs = inboundOrderMapper.selectCount(
                new LambdaQueryWrapper<com.smartwms.entity.InboundOrder>()
                        .eq(com.smartwms.entity.InboundOrder::getSupplierCode, supplierCode)
        );
        if (materialRefs > 0 || applianceRefs > 0 || inboundRefs > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法删除：该供应商已在业务数据中使用");
        }

        supplierMapper.deleteById(id);
    }

    /**
     * 校验供应商编码和名称为必填字段。
     */
    private void validateRequiredFields(Supplier supplier) {
        if (!StringUtils.hasText(supplier.getSupplierCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入供应商编码");
        }
        if (!StringUtils.hasText(supplier.getSupplierName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入供应商名称");
        }
    }

    /**
     * 校验供应商编码唯一性，更新场景可排除当前记录。
     */
    private void validateUniqueCode(String supplierCode, Long excludeId) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, supplierCode);
        if (excludeId != null) {
            wrapper.ne(Supplier::getId, excludeId);
        }
        Long count = supplierMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "供应商编码已存在");
        }
    }
}
