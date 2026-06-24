/**
 * 器具包装参数服务实现。
 *
 * @author Claude
 * @date 2026-06-10
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.Appliance;
import com.smartwms.mapper.ApplianceMapper;
import com.smartwms.service.ApplianceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplianceServiceImpl implements ApplianceService {

    private final ApplianceMapper applianceMapper;

    public ApplianceServiceImpl(ApplianceMapper applianceMapper) {
        this.applianceMapper = applianceMapper;
    }

    /**
     * 分页查询，支持按物料号、供应商编码、包装类型模糊搜索。
     */
    @Override
    public Page<Appliance> page(int current, int size, String materialCode) {
        Page<Appliance> page = new Page<>(current, size);
        LambdaQueryWrapper<Appliance> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(materialCode)) {
            wrapper.like(Appliance::getMaterialCode, materialCode)
                    .or()
                    .like(Appliance::getSupplierCode, materialCode)
                    .or()
                    .like(Appliance::getPackType, materialCode);
        }
        wrapper.orderByDesc(Appliance::getCreatedAt);
        return applianceMapper.selectPage(page, wrapper);
    }

    /**
     * 按主键查询器具详情。
     */
    @Override
    public Appliance getById(Long id) {
        Appliance appliance = applianceMapper.selectById(id);
        if (appliance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        return appliance;
    }

    /**
     * 新增器具，校验必填字段。
     */
    @Override
    public void save(Appliance appliance) {
        validateRequiredFields(appliance);
        applianceMapper.insert(appliance);
    }

    /**
     * 更新器具，先校验记录是否存在。
     */
    @Override
    public void update(Appliance appliance) {
        validateRequiredFields(appliance);
        Appliance existing = applianceMapper.selectById(appliance.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        applianceMapper.updateById(appliance);
    }

    /**
     * 删除器具（无引用校验，器具被引用不影响删除）。
     */
    @Override
    public void delete(Long id) {
        Appliance appliance = applianceMapper.selectById(id);
        if (appliance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "器具配置不存在");
        }
        applianceMapper.deleteById(id);
    }

    /**
     * 校验物料号、供应商编码、包装类型、包装容量为必填。
     */
    private void validateRequiredFields(Appliance appliance) {
        if (!StringUtils.hasText(appliance.getMaterialCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入物料号");
        }
        if (!StringUtils.hasText(appliance.getSupplierCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入供应商编码");
        }
        if (!StringUtils.hasText(appliance.getPackType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入包装器具型号");
        }
        if (appliance.getPackCapacity() == null || appliance.getPackCapacity() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单包装容量必须大于 0");
        }
    }
}
