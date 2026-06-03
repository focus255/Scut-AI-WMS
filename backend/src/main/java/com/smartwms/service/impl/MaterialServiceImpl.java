/**
 * 物料基础信息服务实现。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.common.BusinessException;
import com.smartwms.common.ErrorCode;
import com.smartwms.entity.Material;
import com.smartwms.mapper.InboundDetailMapper;
import com.smartwms.mapper.MaterialMapper;
import com.smartwms.mapper.OutboundDetailMapper;
import com.smartwms.service.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final InboundDetailMapper inboundDetailMapper;
    private final OutboundDetailMapper outboundDetailMapper;

    public MaterialServiceImpl(MaterialMapper materialMapper,
                               InboundDetailMapper inboundDetailMapper,
                               OutboundDetailMapper outboundDetailMapper) {
        this.materialMapper = materialMapper;
        this.inboundDetailMapper = inboundDetailMapper;
        this.outboundDetailMapper = outboundDetailMapper;
    }

    @Override
    public Page<Material> page(int current, int size, String keyword) {
        Page<Material> page = new Page<>(current, size);
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Material::getMaterialCode, keyword)
                   .or()
                   .like(Material::getMaterialName, keyword);
        }
        wrapper.orderByDesc(Material::getCreatedAt);
        return materialMapper.selectPage(page, wrapper);
    }

    @Override
    public Material getById(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        return material;
    }

    @Override
    public void save(Material material) {
        materialMapper.insert(material);
    }

    @Override
    public void update(Material material) {
        Material existing = materialMapper.selectById(material.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        materialMapper.updateById(material);
    }

    /**
     * 删除物料前校验是否已被入出库单据引用。
     */
    @Override
    public void delete(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }

        // 校验是否被入库单引用
        Long inboundRefs = inboundDetailMapper.selectCount(
                new LambdaQueryWrapper<com.smartwms.entity.InboundDetail>()
                        .eq(com.smartwms.entity.InboundDetail::getMaterialCode, material.getMaterialCode())
        );
        // 校验是否被出库单引用
        Long outboundRefs = outboundDetailMapper.selectCount(
                new LambdaQueryWrapper<com.smartwms.entity.OutboundDetail>()
                        .eq(com.smartwms.entity.OutboundDetail::getMaterialCode, material.getMaterialCode())
        );

        if (inboundRefs > 0 || outboundRefs > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "无法删除：该物料已在入出库单据中使用");
        }

        materialMapper.deleteById(id);
    }
}
