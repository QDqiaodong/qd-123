package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.AccessoryService;
import com.factory.security.vo.SafetyStockVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccessoryServiceImpl extends ServiceImpl<AccessoryMapper, Accessory> implements AccessoryService {

    @Autowired
    private ZoneTagMapper zoneTagMapper;

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
    @Transactional(rollbackFor = Exception.class)
    public boolean update(AccessoryDTO dto) {
        validateSpecRange(dto);

        Accessory accessory = new Accessory();
        BeanUtils.copyProperties(dto, accessory);
        boolean result = updateById(accessory);
        // updateById 默认忽略 null 字段：显式同步安全库存下限，保证“清空下限（NULL）”能落库并移出台账
        if (result && dto.getId() != null) {
            baseMapper.updateSafetyStock(dto.getId(), dto.getSafetyStock());
        }
        return result;
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

    @Override
    public List<SafetyStockVO> listSafetyStockShortages(Long zoneTagId, boolean unassignedOnly) {
        // 台账口径：正常配件（@TableLogic 自动过滤已删除）、已设下限（非 null）、现存量低于下限。
        // 用列与列比较直接在 SQL 过滤，未设下限的不查、现存量不低于下限的不查。
        LambdaQueryWrapper<Accessory> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(Accessory::getSafetyStock)
                .apply("stock_quantity < safety_stock");
        // 分区筛选优先下推到 SQL：指定分区按 zone_tag_id 等值；未分配分区先按 IS NULL 粗筛，
        // 分区标签已缺失的悬挂分区在装配 VO 后用 unassignedZone 二次过滤兜住
        if (unassignedOnly) {
            wrapper.isNull(Accessory::getZoneTagId);
        } else if (zoneTagId != null) {
            wrapper.eq(Accessory::getZoneTagId, zoneTagId);
        }

        List<Accessory> accessories = list(wrapper);
        if (accessories.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量装配分区名称：未分配分区（无分区或分区标签已缺失）也必须保留在台账中
        Set<Long> zoneTagIds = accessories.stream()
                .map(Accessory::getZoneTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = zoneTagIds.isEmpty()
                ? Collections.emptyMap()
                : zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                        .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));

        return accessories.stream()
                .map(accessory -> buildSafetyStockVO(accessory, zoneTagMap))
                // 未分配分区筛选与页面/导出同源：悬挂分区（zone_tag_id 非空但分区标签已删除）
                // 在 SQL 粗筛后仍可能漏入，这里按装配出的 unassignedZone 标识二次过滤
                .filter(row -> !unassignedOnly || Boolean.TRUE.equals(row.getUnassignedZone()))
                // 采购按分区领料：仍保持分区顺序（分区排序号升序、未分配分区殿后），
                // 但同一分区内缺口从大到小，缺口大的急件不混在普通行里；
                // 缺口相同再按名称、ID 稳定排序。换分区或改下限刷新后，顺序随新缺口重排
                .sorted(Comparator
                        .comparingLong((SafetyStockVO row) -> {
                            if (Boolean.TRUE.equals(row.getUnassignedZone())) {
                                return Long.MAX_VALUE;
                            }
                            ZoneTag zoneTag = row.getZoneTagId() == null
                                    ? null : zoneTagMap.get(row.getZoneTagId());
                            return zoneTag != null && zoneTag.getSortOrder() != null
                                    ? zoneTag.getSortOrder() : Long.MAX_VALUE;
                        })
                        .thenComparing(row -> {
                            ZoneTag zoneTag = row.getZoneTagId() == null
                                    ? null : zoneTagMap.get(row.getZoneTagId());
                            return zoneTag != null && zoneTag.getTagName() != null
                                    ? zoneTag.getTagName() : "";
                        })
                        .thenComparing(SafetyStockVO::getGapQuantity,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(row -> row.getAccessoryName() != null ? row.getAccessoryName() : "")
                        .thenComparing(SafetyStockVO::getAccessoryId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private SafetyStockVO buildSafetyStockVO(Accessory accessory, Map<Long, ZoneTag> zoneTagMap) {
        SafetyStockVO vo = new SafetyStockVO();
        vo.setAccessoryId(accessory.getId());
        vo.setAccessoryName(accessory.getAccessoryName());
        vo.setModel(accessory.getModel());
        vo.setSpecUnit(accessory.getSpecUnit());
        vo.setZoneTagId(accessory.getZoneTagId());

        ZoneTag zoneTag = accessory.getZoneTagId() == null
                ? null : zoneTagMap.get(accessory.getZoneTagId());
        boolean unassigned = zoneTag == null;
        if (!unassigned) {
            vo.setZoneTagName(zoneTag.getTagName());
        }
        vo.setUnassignedZone(unassigned);

        int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
        int safetyStock = accessory.getSafetyStock() == null ? 0 : accessory.getSafetyStock();
        vo.setStockQuantity(stock);
        vo.setSafetyStock(safetyStock);
        // 进入台账即现存量 < 下限，缺口恒大于 0
        int gap = safetyStock - stock;
        vo.setGapQuantity(gap);
        // 紧急口径：缺口达到下限一半及以上（2*缺口 >= 下限，含恰好一半的边界）；
        // 每次实时计算，改下限或换分区刷新后紧急标记与新缺口一致
        vo.setUrgent(2 * gap >= safetyStock);
        vo.setCreateTime(accessory.getCreateTime());
        return vo;
    }

    private void validateSpecRange(AccessoryDTO dto) {
        if (dto.getSpecMin() != null && dto.getSpecMax() != null) {
            if (dto.getSpecMin().compareTo(dto.getSpecMax()) > 0) {
                throw new RuntimeException("规格最小值不能大于最大值");
            }
        }
    }
}
