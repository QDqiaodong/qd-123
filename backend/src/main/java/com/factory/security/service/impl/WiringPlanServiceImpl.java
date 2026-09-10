package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.dto.WiringPlanDetailDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.WiringPlan;
import com.factory.security.entity.WiringPlanDetail;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.WiringPlanDetailMapper;
import com.factory.security.mapper.WiringPlanMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.WiringPlanDetailVO;
import com.factory.security.vo.WiringPlanExportRowVO;
import com.factory.security.vo.WiringPlanVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WiringPlanServiceImpl extends ServiceImpl<WiringPlanMapper, WiringPlan> implements WiringPlanService {

    @Autowired
    private WiringPlanDetailMapper wiringPlanDetailMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Override
    public Page<WiringPlanVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<WiringPlan> page = new Page<>(pageNum, pageSize);
        Page<WiringPlan> planPage = page(page, buildFilterWrapper(keyword, status));

        Page<WiringPlanVO> voPage = new Page<>(planPage.getCurrent(), planPage.getSize(), planPage.getTotal());
        List<WiringPlanVO> voList = planPage.getRecords().stream().map(plan -> {
            WiringPlanVO vo = new WiringPlanVO();
            BeanUtils.copyProperties(plan, vo);
            vo.setDetailCount(countDetails(plan.getId()));
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public WiringPlanVO getDetailById(Long id) {
        WiringPlan plan = getById(id);
        if (plan == null) {
            throw new RuntimeException("布线方案不存在");
        }

        WiringPlanVO vo = new WiringPlanVO();
        BeanUtils.copyProperties(plan, vo);

        List<WiringPlanDetailVO> details = listDetailVOs(id);
        vo.setDetails(details);
        vo.setDetailCount(details.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean add(WiringPlanDTO dto) {
        validateDetails(dto.getDetails());

        WiringPlan plan = new WiringPlan();
        BeanUtils.copyProperties(dto, plan);
        plan.setId(null);
        boolean result = save(plan);
        if (result) {
            saveDetails(plan.getId(), dto.getDetails());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(WiringPlanDTO dto) {
        if (dto.getId() == null || getById(dto.getId()) == null) {
            throw new RuntimeException("布线方案不存在");
        }
        validateDetails(dto.getDetails());

        WiringPlan plan = new WiringPlan();
        BeanUtils.copyProperties(dto, plan);
        boolean result = updateById(plan);
        if (result) {
            LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(WiringPlanDetail::getPlanId, dto.getId());
            wiringPlanDetailMapper.delete(wrapper);
            saveDetails(dto.getId(), dto.getDetails());
        }
        return result;
    }

    @Override
    public boolean delete(Long id) {
        int count = countDetails(id);
        if (count > 0) {
            throw new RuntimeException("该方案存在关联配件明细，无法直接删除，请先移除方案内的配件明细");
        }
        return removeById(id);
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new RuntimeException("启用状态不合法");
        }
        WiringPlan existing = getById(id);
        if (existing == null) {
            throw new RuntimeException("布线方案不存在或已被删除");
        }
        WiringPlan plan = new WiringPlan();
        plan.setId(id);
        plan.setStatus(status);
        boolean result = updateById(plan);
        if (!result) {
            throw new RuntimeException("布线方案状态更新失败，请刷新后重试");
        }
        return true;
    }

    @Override
    public List<WiringPlanExportRowVO> listExportRows(String keyword, Integer status) {
        List<WiringPlan> plans = list(buildFilterWrapper(keyword, status));
        if (plans.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量装配全部方案明细、配件、分区信息，避免逐方案查询
        List<Long> planIds = plans.stream().map(WiringPlan::getId).collect(Collectors.toList());
        LambdaQueryWrapper<WiringPlanDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.in(WiringPlanDetail::getPlanId, planIds);
        List<WiringPlanDetail> allDetails = wiringPlanDetailMapper.selectList(detailWrapper);

        Map<Long, List<WiringPlanDetail>> detailsByPlan = allDetails.stream()
                .collect(Collectors.groupingBy(WiringPlanDetail::getPlanId));

        Set<Long> accessoryIds = allDetails.stream()
                .map(WiringPlanDetail::getAccessoryId)
                .collect(Collectors.toSet());
        Map<Long, Accessory> accessoryMap = accessoryIds.isEmpty()
                ? Collections.emptyMap()
                : accessoryMapper.selectBatchIds(accessoryIds).stream()
                        .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        Set<Long> zoneTagIds = accessoryMap.values().stream()
                .map(Accessory::getZoneTagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = zoneTagIds.isEmpty()
                ? Collections.emptyMap()
                : zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                        .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));

        List<WiringPlanExportRowVO> rows = new ArrayList<>();
        for (WiringPlan plan : plans) {
            List<WiringPlanDetail> details = detailsByPlan.getOrDefault(plan.getId(), Collections.emptyList());
            details = sortDetailsByZone(details, accessoryMap, zoneTagMap);
            rows.addAll(buildExportRows(plan, details, accessoryMap, zoneTagMap));
        }
        return rows;
    }

    /**
     * 构造与分页列表一致的筛选条件：关键词模糊匹配方案名称/适用场景，启用状态精确匹配，
     * 方案按创建时间倒序排列，保证导出的方案顺序与列表一致
     */
    private LambdaQueryWrapper<WiringPlan> buildFilterWrapper(String keyword, Integer status) {
        LambdaQueryWrapper<WiringPlan> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(WiringPlan::getPlanName, keyword)
                    .or().like(WiringPlan::getScene, keyword));
        }
        if (status != null) {
            wrapper.eq(WiringPlan::getStatus, status);
        }
        wrapper.orderByDesc(WiringPlan::getCreateTime);
        wrapper.orderByDesc(WiringPlan::getId);
        return wrapper;
    }

    /**
     * 同一方案的配件按库房分区排序：先按分区排序号升序，未分配分区排最后；
     * 同一分区内按配件名称、再按明细 ID 排序，保证多次导出顺序稳定
     */
    private List<WiringPlanDetail> sortDetailsByZone(List<WiringPlanDetail> details,
                                                     Map<Long, Accessory> accessoryMap,
                                                     Map<Long, ZoneTag> zoneTagMap) {
        return details.stream()
                .sorted(Comparator
                        // 未分配分区（无分区或配件已删除）排最后，其余按分区排序号升序
                        .comparingLong((WiringPlanDetail d) -> {
                            ZoneTag zoneTag = resolveZoneTag(d, accessoryMap, zoneTagMap);
                            return zoneTag != null ? zoneTagSortKey(zoneTag) : Long.MAX_VALUE;
                        })
                        // 同一分区内按分区名（排序号相同时）、配件名称、明细 ID 排序，保证顺序稳定
                        .thenComparing(d -> {
                            ZoneTag zoneTag = resolveZoneTag(d, accessoryMap, zoneTagMap);
                            return zoneTag != null && zoneTag.getTagName() != null ? zoneTag.getTagName() : "";
                        })
                        .thenComparing(d -> {
                            Accessory accessory = accessoryMap.get(d.getAccessoryId());
                            return accessory != null && accessory.getAccessoryName() != null
                                    ? accessory.getAccessoryName() : "";
                        })
                        .thenComparing(WiringPlanDetail::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private ZoneTag resolveZoneTag(WiringPlanDetail detail,
                                   Map<Long, Accessory> accessoryMap,
                                   Map<Long, ZoneTag> zoneTagMap) {
        Accessory accessory = accessoryMap.get(detail.getAccessoryId());
        if (accessory == null || accessory.getZoneTagId() == null) {
            return null;
        }
        return zoneTagMap.get(accessory.getZoneTagId());
    }

    private long zoneTagSortKey(ZoneTag zoneTag) {
        return zoneTag.getSortOrder() != null ? zoneTag.getSortOrder() : Integer.MAX_VALUE;
    }

    /**
     * 将一个方案展开为多行：方案信息逐行重复以保留方案边界；
     * 方案没有任何配件时输出一条仅含方案信息的占位行，避免方案在导出结果中“消失”
     */
    private List<WiringPlanExportRowVO> buildExportRows(WiringPlan plan,
                                                        List<WiringPlanDetail> details,
                                                        Map<Long, Accessory> accessoryMap,
                                                        Map<Long, ZoneTag> zoneTagMap) {
        List<WiringPlanExportRowVO> rows = new ArrayList<>();
        if (details.isEmpty()) {
            rows.add(buildPlanRow(plan, null, null, null, null));
            return rows;
        }
        for (WiringPlanDetail detail : details) {
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            String accessoryName = accessory != null ? accessory.getAccessoryName() : "配件已删除";
            String specUnit = accessory != null ? accessory.getSpecUnit() : null;
            ZoneTag zoneTag = resolveZoneTag(detail, accessoryMap, zoneTagMap);
            String zoneName = zoneTag != null ? zoneTag.getTagName() : "未分配分区";
            rows.add(buildPlanRow(plan, accessoryName, zoneName, String.valueOf(detail.getQuantity()), specUnit));
        }
        return rows;
    }

    private WiringPlanExportRowVO buildPlanRow(WiringPlan plan, String accessoryName, String zoneName,
                                               String quantityText, String specUnit) {
        WiringPlanExportRowVO row = new WiringPlanExportRowVO();
        row.setPlanId(plan.getId());
        row.setPlanName(plan.getPlanName());
        row.setScene(plan.getScene() == null ? "" : plan.getScene());
        row.setStatusText(plan.getStatus() != null && plan.getStatus() == 1 ? "启用" : "停用");
        row.setAccessoryName(accessoryName == null ? "" : accessoryName);
        row.setZoneTagName(zoneName == null ? "" : zoneName);
        row.setQuantityText(quantityText == null ? "" : quantityText);
        row.setSpecUnit(specUnit == null ? "" : specUnit);
        return row;
    }

    private int countDetails(Long planId) {
        LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WiringPlanDetail::getPlanId, planId);
        return Math.toIntExact(wiringPlanDetailMapper.selectCount(wrapper));
    }

    private List<WiringPlanDetailVO> listDetailVOs(Long planId) {
        LambdaQueryWrapper<WiringPlanDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WiringPlanDetail::getPlanId, planId);
        List<WiringPlanDetail> details = wiringPlanDetailMapper.selectList(wrapper);
        if (details.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> accessoryIds = details.stream()
                .map(WiringPlanDetail::getAccessoryId)
                .collect(Collectors.toSet());
        Map<Long, Accessory> accessoryMap = accessoryMapper.selectBatchIds(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        Set<Long> zoneTagIds = accessoryMap.values().stream()
                .map(Accessory::getZoneTagId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = zoneTagIds.isEmpty()
                ? Collections.emptyMap()
                : zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                        .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));

        return details.stream().map(detail -> {
            WiringPlanDetailVO vo = new WiringPlanDetailVO();
            BeanUtils.copyProperties(detail, vo);
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            if (accessory != null) {
                vo.setAccessoryName(accessory.getAccessoryName());
                vo.setModel(accessory.getModel());
                vo.setSpecUnit(accessory.getSpecUnit());
                vo.setZoneTagId(accessory.getZoneTagId());
                ZoneTag zoneTag = zoneTagMap.get(accessory.getZoneTagId());
                if (zoneTag != null) {
                    vo.setZoneTagName(zoneTag.getTagName());
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private void validateDetails(List<WiringPlanDetailDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        Set<Long> accessoryIds = new HashSet<>();
        for (WiringPlanDetailDTO detail : details) {
            if (detail.getAccessoryId() == null) {
                throw new RuntimeException("配件不能为空");
            }
            if (detail.getQuantity() == null || detail.getQuantity() < 1) {
                throw new RuntimeException("需求数量必须为正整数");
            }
            if (!accessoryIds.add(detail.getAccessoryId())) {
                throw new RuntimeException("同一方案内配件不可重复");
            }
        }

        List<Accessory> accessories = accessoryMapper.selectBatchIds(accessoryIds);
        if (accessories.size() != accessoryIds.size()) {
            throw new RuntimeException("所选配件不存在或已删除");
        }
    }

    private void saveDetails(Long planId, List<WiringPlanDetailDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (WiringPlanDetailDTO dto : details) {
            WiringPlanDetail detail = new WiringPlanDetail();
            detail.setPlanId(planId);
            detail.setAccessoryId(dto.getAccessoryId());
            detail.setQuantity(dto.getQuantity());
            wiringPlanDetailMapper.insert(detail);
        }
    }
}
