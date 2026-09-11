package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.service.AccessoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccessoryServiceImpl extends ServiceImpl<AccessoryMapper, Accessory> implements AccessoryService {

    @Override
    public Page<Accessory> page(Integer pageNum, Integer pageSize, String keyword, Long zoneTagId) {
        Page<Accessory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Accessory> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Accessory::getAccessoryName, keyword)
                    .or().like(Accessory::getModel, keyword)
                    .or().like(Accessory::getMaterial, keyword));
        }

        if (zoneTagId != null) {
            wrapper.eq(Accessory::getZoneTagId, zoneTagId);
        }

        wrapper.orderByDesc(Accessory::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public boolean add(AccessoryDTO dto) {
        validateSpecRange(dto);

        Accessory accessory = new Accessory();
        BeanUtils.copyProperties(dto, accessory);
        return save(accessory);
    }

    @Override
    public boolean update(AccessoryDTO dto) {
        validateSpecRange(dto);

        Accessory accessory = new Accessory();
        BeanUtils.copyProperties(dto, accessory);
        return updateById(accessory);
    }

    @Override
    public boolean updateZone(Long id, Long zoneTagId) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setZoneTagId(zoneTagId);
        return updateById(accessory);
    }

    @Override
    public boolean delete(Long id) {
        // 软删除：已被方案引用的配件删除后仍在方案明细与缺口列表中展示，但不可再被新增方案选择或核销出库
        return removeById(id);
    }

    private void validateSpecRange(AccessoryDTO dto) {
        if (dto.getSpecMin() != null && dto.getSpecMax() != null) {
            if (dto.getSpecMin().compareTo(dto.getSpecMax()) > 0) {
                throw new RuntimeException("规格最小值不能大于最大值");
            }
        }
    }
}
