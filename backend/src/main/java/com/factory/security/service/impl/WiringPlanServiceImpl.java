package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.dto.WiringPlanDetailDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.StockWriteoff;
import com.factory.security.entity.WiringPlan;
import com.factory.security.entity.WiringPlanDetail;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.StockWriteoffMapper;
import com.factory.security.mapper.WiringPlanDetailMapper;
import com.factory.security.mapper.WiringPlanMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.StockGapVO;
import com.factory.security.vo.StockGapZoneSummaryVO;
import com.factory.security.vo.WiringPlanDetailVO;
import com.factory.security.vo.WiringPlanExportRowVO;
import com.factory.security.vo.WiringPlanVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class WiringPlanServiceImpl extends ServiceImpl<WiringPlanMapper, WiringPlan> implements WiringPlanService {

    /** 分区汇总中“未分配分区”分组的归组键（zoneTagId 为 null 时的占位键） */
    private static final String UNASSIGNED_ZONE_KEY = "__UNASSIGNED_ZONE__";

    @Autowired
    private WiringPlanDetailMapper wiringPlanDetailMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Autowired
    private StockWriteoffMapper stockWriteoffMapper;

    @Override
    public Page<WiringPlanVO> page(Integer pageNum, Integer pageSize, String keyword, Integer status) {
        Page<WiringPlan> page = new Page<>(pageNum, pageSize);
        Page<WiringPlan> planPage = page(page, buildFilterWrapper(keyword, status));

        List<WiringPlan> plans = planPage.getRecords();
        // 批量装配核销记录、明细与库存，避免逐方案查询；与缺口列表共用同一套统计口径
        Map<Long, StockWriteoff> writeoffMap = listWriteoffsByPlanIds(
                plans.stream().map(WiringPlan::getId).collect(Collectors.toList()));
        Map<Long, List<WiringPlanDetail>> detailsByPlan = listDetailsByPlanIds(
                plans.stream().map(WiringPlan::getId).collect(Collectors.toList()));
        Set<Long> detailAccessoryIds = detailsByPlan.values().stream()
                .flatMap(List::stream)
                .map(WiringPlanDetail::getAccessoryId)
                .collect(Collectors.toSet());
        Map<Long, Accessory> accessoryMap = detailAccessoryIds.isEmpty()
                ? Collections.emptyMap()
                : accessoryMapper.selectBatchIds(detailAccessoryIds).stream()
                        .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        Page<WiringPlanVO> voPage = new Page<>(planPage.getCurrent(), planPage.getSize(), planPage.getTotal());
        List<WiringPlanVO> voList = plans.stream().map(plan -> {
            WiringPlanVO vo = new WiringPlanVO();
            BeanUtils.copyProperties(plan, vo);
            List<WiringPlanDetail> details = detailsByPlan.getOrDefault(plan.getId(), Collections.emptyList());
            vo.setDetailCount(details.size());
            applyWriteoffInfo(vo, plan, details, writeoffMap.get(plan.getId()), accessoryMap);
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

        // 核销状态与库存充足性与列表、缺口列表保持同一口径，刷新后三处一致
        StockWriteoff writeoff = getWriteoffByPlanId(id);
        Set<Long> existingAccessoryIds = details.stream()
                .filter(d -> !Boolean.TRUE.equals(d.getAccessoryDeleted()))
                .map(WiringPlanDetailVO::getAccessoryId)
                .collect(Collectors.toSet());
        Map<Long, Accessory> accessoryMap = existingAccessoryIds.isEmpty()
                ? Collections.emptyMap()
                : accessoryMapper.selectBatchIds(existingAccessoryIds).stream()
                        .collect(Collectors.toMap(Accessory::getId, Function.identity()));
        List<WiringPlanDetail> rawDetails = details.stream().map(d -> {
            WiringPlanDetail raw = new WiringPlanDetail();
            raw.setId(d.getId());
            raw.setPlanId(d.getPlanId());
            raw.setAccessoryId(d.getAccessoryId());
            raw.setQuantity(d.getQuantity());
            return raw;
        }).collect(Collectors.toList());
        applyWriteoffInfo(vo, plan, rawDetails, writeoff, accessoryMap);
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
        if (getWriteoffByPlanId(dto.getId()) != null) {
            // 已核销出库的方案已实际扣减库存，修改明细会破坏库存账实一致
            throw new RuntimeException("该方案已核销出库，不可修改，如需调整请新建方案");
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
        if (getWriteoffByPlanId(id) != null) {
            // 已核销出库的方案是库存扣减凭证，不可删除
            throw new RuntimeException("该方案已核销出库，核销记录需保留，无法删除");
        }
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
        if (getWriteoffByPlanId(id) != null) {
            // 已核销出库的方案是库存扣减凭证：停用会让其从启用筛选的列表与导出中消失，
            // 对账时与已扣减的库存对不上，故启用状态锁定不可变更
            throw new RuntimeException("该方案已核销出库，现存量已扣减，不可变更启用状态");
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
                .filter(Objects::nonNull)
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

    // -------------------- 库存缺口与核销出库 --------------------

    @Override
    public List<StockGapVO> listStockGaps() {
        // 需求合计口径：只统计已启用且未核销的方案；停用方案、已核销方案不参与合计
        List<WiringPlan> activePlans = list(new LambdaQueryWrapper<WiringPlan>()
                .eq(WiringPlan::getStatus, 1));
        Set<Long> writtenOffPlanIds = listWriteoffsByPlanIds(
                activePlans.stream().map(WiringPlan::getId).collect(Collectors.toList())).keySet();
        List<Long> countingPlanIds = activePlans.stream()
                .map(WiringPlan::getId)
                .filter(planId -> !writtenOffPlanIds.contains(planId))
                .collect(Collectors.toList());

        Map<Long, Integer> requiredMap = sumRequiredQuantity(countingPlanIds);

        // 正常配件全部列出（无需求则需求合计为 0）；另需带上被参与合计的方案引用的已删除配件
        List<Accessory> activeAccessories = accessoryMapper.selectList(new LambdaQueryWrapper<>());
        Set<Long> presentIds = activeAccessories.stream().map(Accessory::getId).collect(Collectors.toSet());
        List<Long> missingReferencedIds = requiredMap.keySet().stream()
                .filter(accessoryId -> !presentIds.contains(accessoryId))
                .collect(Collectors.toList());
        List<Accessory> deletedAccessories = missingReferencedIds.isEmpty()
                ? Collections.emptyList()
                : accessoryMapper.selectAllByIdsIncludingDeleted(missingReferencedIds);
        // 极端情况下配件物理缺失（自定义 SQL 仍查不到）时仍保留一行占位，保证缺口列表与方案明细一致
        Set<Long> loadedDeletedIds = deletedAccessories.stream().map(Accessory::getId).collect(Collectors.toSet());
        missingReferencedIds.stream()
                .filter(accessoryId -> !loadedDeletedIds.contains(accessoryId))
                .map(accessoryId -> {
                    Accessory placeholder = new Accessory();
                    placeholder.setId(accessoryId);
                    placeholder.setAccessoryName("配件已删除");
                    placeholder.setStockQuantity(0);
                    return placeholder;
                })
                .forEach(deletedAccessories::add);

        Map<Long, ZoneTag> zoneTagMap = loadZoneTagMap(
                Stream.concat(activeAccessories.stream(), deletedAccessories.stream())
                        .map(Accessory::getZoneTagId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));

        List<StockGapVO> rows = new ArrayList<>();
        Stream.concat(activeAccessories.stream(), deletedAccessories.stream())
                .map(accessory -> buildGapVO(accessory, requiredMap, zoneTagMap))
                .forEach(rows::add);

        // 排序与方案明细/导出一致：分区排序号升序、同分区按配件名称、未分配分区最后
        rows.sort(Comparator
                .comparingLong((StockGapVO row) -> {
                    if (Boolean.TRUE.equals(row.getUnassignedZone())) {
                        return Long.MAX_VALUE;
                    }
                    ZoneTag zoneTag = row.getZoneTagId() == null ? null : zoneTagMap.get(row.getZoneTagId());
                    return zoneTag != null && zoneTag.getSortOrder() != null
                            ? zoneTag.getSortOrder() : Long.MAX_VALUE;
                })
                .thenComparing(row -> {
                    ZoneTag zoneTag = row.getZoneTagId() == null ? null : zoneTagMap.get(row.getZoneTagId());
                    return zoneTag != null && zoneTag.getTagName() != null ? zoneTag.getTagName() : "";
                })
                .thenComparing(row -> row.getAccessoryName() != null ? row.getAccessoryName() : "")
                .thenComparing(StockGapVO::getAccessoryId, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    @Override
    public List<StockGapZoneSummaryVO> listStockGapZoneSummary() {
        // 与缺口列表共用同一批数据归组：缺口列表已按“分区排序号升序、未分配分区最后”排序，
        // 按出现顺序归组即得分区小计顺序，保证页面合计、分区小计与导出文件一致
        List<StockGapVO> gaps = listStockGaps();
        Map<String, StockGapZoneSummaryVO> summaryByZone = new LinkedHashMap<>();
        for (StockGapVO gap : gaps) {
            boolean unassigned = Boolean.TRUE.equals(gap.getUnassignedZone());
            String key = unassigned ? UNASSIGNED_ZONE_KEY : String.valueOf(gap.getZoneTagId());
            StockGapZoneSummaryVO summary = summaryByZone.computeIfAbsent(key, k -> {
                StockGapZoneSummaryVO vo = new StockGapZoneSummaryVO();
                vo.setZoneTagId(unassigned ? null : gap.getZoneTagId());
                vo.setZoneTagName(unassigned ? "未分配分区" : gap.getZoneTagName());
                vo.setUnassignedZone(unassigned);
                vo.setShortageAccessoryCount(0);
                vo.setGapQuantityTotal(0);
                return vo;
            });
            // 只统计现存量不足的配件：已删除配件 shortage=false、缺口为 0，自然不参与合计
            if (Boolean.TRUE.equals(gap.getShortage())) {
                summary.setShortageAccessoryCount(summary.getShortageAccessoryCount() + 1);
                summary.setGapQuantityTotal(summary.getGapQuantityTotal()
                        + (gap.getGapQuantity() == null ? 0 : gap.getGapQuantity()));
            }
        }
        return new ArrayList<>(summaryByZone.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean writeoff(Long id) {
        WiringPlan plan = getById(id);
        if (plan == null) {
            throw new RuntimeException("布线方案不存在或已被删除");
        }
        if (plan.getStatus() == null || plan.getStatus() != 1) {
            // 停用方案不参与需求合计，自然也不能核销出库
            throw new RuntimeException("停用状态的方案不可核销出库，请先启用方案");
        }
        if (getWriteoffByPlanId(id) != null) {
            throw new RuntimeException("该方案已核销出库，同一方案不可重复核销");
        }

        List<WiringPlanDetail> details = wiringPlanDetailMapper.selectList(
                new LambdaQueryWrapper<WiringPlanDetail>().eq(WiringPlanDetail::getPlanId, id));
        if (details.isEmpty()) {
            throw new RuntimeException("该方案没有配件明细，无法核销出库");
        }

        Map<Long, Accessory> accessoryMap = accessoryMapper.selectBatchIds(
                        details.stream().map(WiringPlanDetail::getAccessoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        for (WiringPlanDetail detail : details) {
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            // 已删除配件仍在明细中展示，但不允许据此核销
            if (accessory == null) {
                throw new RuntimeException("方案包含已删除的配件，无法核销，请先调整方案明细");
            }
            int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            if (stock < detail.getQuantity()) {
                throw new RuntimeException(String.format(
                        "配件「%s」现存量不足（现存 %d，需求 %d），无法核销出库，请补充库存或调整方案",
                        accessory.getAccessoryName(), stock, detail.getQuantity()));
            }
        }

        // 条件更新扣减库存：行级条件防止并发核销把库存扣成负数；任一明细失败则整体回滚
        for (WiringPlanDetail detail : details) {
            int affected = accessoryMapper.deductStock(detail.getAccessoryId(), detail.getQuantity());
            if (affected != 1) {
                throw new RuntimeException("配件现存量已变动且不足出库，请刷新缺口列表后重试");
            }
        }

        StockWriteoff writeoff = new StockWriteoff();
        writeoff.setPlanId(id);
        writeoff.setPlanName(plan.getPlanName());
        try {
            stockWriteoffMapper.insert(writeoff);
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已核销同一方案，交由事务回滚库存扣减
            throw new RuntimeException("该方案已核销出库，同一方案不可重复核销");
        }
        return true;
    }

    /**
     * 构造筛选条件：关键词模糊匹配方案名称/适用场景，启用状态精确匹配，
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
        // 逻辑删除的配件不会被 selectBatchIds 查出，明细行保留并以兜底文案展示
        Map<Long, Accessory> accessoryMap = accessoryMapper.selectBatchIds(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity()));

        Set<Long> zoneTagIds = accessoryMap.values().stream()
                .map(Accessory::getZoneTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = zoneTagIds.isEmpty()
                ? Collections.emptyMap()
                : zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                        .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));

        // 与导出共用同一套分区排序：分区排序号升序、同分区按配件名称、未分配分区最后，
        // 避免刷新或编辑后详情页分组顺序依赖数据库返回顺序而与导出结果不一致
        List<WiringPlanDetail> sortedDetails = sortDetailsByZone(details, accessoryMap, zoneTagMap);

        return sortedDetails.stream().map(detail -> {
            WiringPlanDetailVO vo = new WiringPlanDetailVO();
            BeanUtils.copyProperties(detail, vo);
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            if (accessory != null) {
                vo.setAccessoryName(accessory.getAccessoryName());
                vo.setModel(accessory.getModel());
                vo.setSpecUnit(accessory.getSpecUnit());
                vo.setZoneTagId(accessory.getZoneTagId());
                vo.setStockQuantity(accessory.getStockQuantity());
                vo.setAccessoryDeleted(false);
                ZoneTag zoneTag = zoneTagMap.get(accessory.getZoneTagId());
                if (zoneTag != null) {
                    vo.setZoneTagName(zoneTag.getTagName());
                }
            } else {
                // 配件已被删除：与导出一致给出明确兜底文案，分区留空由前端归入“未分配分区”；
                // 已删除配件仍显示但不可核销出库
                vo.setAccessoryName("配件已删除");
                vo.setAccessoryDeleted(true);
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

    // -------------------- 核销/缺口装配辅助方法 --------------------

    private StockWriteoff getWriteoffByPlanId(Long planId) {
        return stockWriteoffMapper.selectOne(new LambdaQueryWrapper<StockWriteoff>()
                .eq(StockWriteoff::getPlanId, planId)
                .last("LIMIT 1"));
    }

    private Map<Long, StockWriteoff> listWriteoffsByPlanIds(Collection<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return stockWriteoffMapper.selectList(new LambdaQueryWrapper<StockWriteoff>()
                        .in(StockWriteoff::getPlanId, planIds)).stream()
                .collect(Collectors.toMap(StockWriteoff::getPlanId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, List<WiringPlanDetail>> listDetailsByPlanIds(Collection<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wiringPlanDetailMapper.selectList(new LambdaQueryWrapper<WiringPlanDetail>()
                        .in(WiringPlanDetail::getPlanId, planIds)).stream()
                .collect(Collectors.groupingBy(WiringPlanDetail::getPlanId));
    }

    /**
     * 按参与合计的方案集合汇总每个配件的需求数量
     */
    private Map<Long, Integer> sumRequiredQuantity(Collection<Long> countingPlanIds) {
        if (countingPlanIds == null || countingPlanIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Integer> requiredMap = new LinkedHashMap<>();
        List<WiringPlanDetail> details = wiringPlanDetailMapper.selectList(
                new LambdaQueryWrapper<WiringPlanDetail>()
                        .in(WiringPlanDetail::getPlanId, countingPlanIds));
        for (WiringPlanDetail detail : details) {
            requiredMap.merge(detail.getAccessoryId(), detail.getQuantity(), Integer::sum);
        }
        return requiredMap;
    }

    private Map<Long, ZoneTag> loadZoneTagMap(Set<Long> zoneTagIds) {
        if (zoneTagIds == null || zoneTagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                .collect(Collectors.toMap(ZoneTag::getId, Function.identity()));
    }

    /**
     * 填充方案 VO 的核销状态与库存充足性。库存充足性仅对“启用且未核销”的方案判定，
     * 与缺口列表完全同一口径，保证刷新后方案列表、方案详情、缺口列表一致
     */
    private void applyWriteoffInfo(WiringPlanVO vo, WiringPlan plan, List<WiringPlanDetail> details,
                                   StockWriteoff writeoff, Map<Long, Accessory> accessoryMap) {
        vo.setWriteoff(writeoff != null);
        vo.setWriteoffTime(writeoff != null ? writeoff.getCreateTime() : null);

        boolean eligible = plan.getStatus() != null && plan.getStatus() == 1 && writeoff == null;
        if (!eligible) {
            vo.setStockSufficient(true);
            vo.setHasDeletedAccessory(false);
            return;
        }
        boolean hasDeleted = false;
        boolean sufficient = true;
        for (WiringPlanDetail detail : details) {
            Accessory accessory = accessoryMap.get(detail.getAccessoryId());
            if (accessory == null) {
                hasDeleted = true;
                sufficient = false;
                continue;
            }
            int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            if (stock < detail.getQuantity()) {
                sufficient = false;
            }
        }
        vo.setHasDeletedAccessory(hasDeleted);
        vo.setStockSufficient(sufficient);
    }

    private StockGapVO buildGapVO(Accessory accessory, Map<Long, Integer> requiredMap,
                                  Map<Long, ZoneTag> zoneTagMap) {
        StockGapVO vo = new StockGapVO();
        vo.setAccessoryId(accessory.getId());
        boolean deleted = accessory.getDeleted() != null && accessory.getDeleted() == 1;
        boolean unassigned = accessory.getZoneTagId() == null
                || zoneTagMap.get(accessory.getZoneTagId()) == null;
        int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
        int required = requiredMap.getOrDefault(accessory.getId(), 0);
        int gap = Math.max(0, required - stock);

        vo.setAccessoryName(accessory.getAccessoryName());
        vo.setModel(accessory.getModel());
        vo.setSpecUnit(accessory.getSpecUnit());
        vo.setZoneTagId(accessory.getZoneTagId());
        ZoneTag zoneTag = accessory.getZoneTagId() == null ? null : zoneTagMap.get(accessory.getZoneTagId());
        if (zoneTag != null) {
            vo.setZoneTagName(zoneTag.getTagName());
        }
        vo.setStockQuantity(stock);
        vo.setRequiredQuantity(required);
        // 已删除配件仅列示提示，不参与缺口标红与核销
        vo.setGapQuantity(deleted ? 0 : gap);
        vo.setShortage(!deleted && gap > 0);
        vo.setUnassignedZone(unassigned);
        vo.setAccessoryDeleted(deleted);
        vo.setCreateTime(accessory.getCreateTime());
        return vo;
    }
}
