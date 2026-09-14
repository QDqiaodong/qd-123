package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.ReplenishCancelDTO;
import com.factory.security.dto.ReplenishCreateDTO;
import com.factory.security.dto.ReplenishItemDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ReplenishOrder;
import com.factory.security.entity.ReplenishOrderItem;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.ReplenishOrderItemMapper;
import com.factory.security.mapper.ReplenishOrderMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.ReplenishOrderService;
import com.factory.security.vo.ReplenishOrderDetailVO;
import com.factory.security.vo.ReplenishOrderItemVO;
import com.factory.security.vo.ReplenishOrderVO;
import com.factory.security.vo.ReplenishZoneSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReplenishOrderServiceImpl extends ServiceImpl<ReplenishOrderMapper, ReplenishOrder>
        implements ReplenishOrderService {

    /** 未分配分区的展示名称与生成单据时的快照名 */
    private static final String UNASSIGNED_ZONE_NAME = "未分配分区";

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private ReplenishOrderItemMapper replenishOrderItemMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Override
    public Page<ReplenishOrderVO> page(Integer pageNum, Integer pageSize, Integer status) {
        Page<ReplenishOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReplenishOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ReplenishOrder::getStatus, status);
        }
        wrapper.orderByDesc(ReplenishOrder::getCreateTime);
        wrapper.orderByDesc(ReplenishOrder::getId);
        Page<ReplenishOrder> orderPage = page(page, wrapper);

        List<ReplenishOrder> orders = orderPage.getRecords();
        Map<Long, Integer> zoneCountMap = countZonesByOrderIds(
                orders.stream().map(ReplenishOrder::getId).collect(Collectors.toList()));

        Page<ReplenishOrderVO> voPage = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        voPage.setRecords(orders.stream()
                .map(order -> buildHeaderVO(order, zoneCountMap.getOrDefault(order.getId(), 0)))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReplenishCreateDTO dto) {
        // 去重勾选：同一配件在一次请求里重复勾选只保留一行
        List<Long> accessoryIds = dto.getItems().stream()
                .map(ReplenishCreateDTO.ReplenishItemCreateDTO::getAccessoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (accessoryIds.isEmpty()) {
            throw new RuntimeException("请至少勾选一个配件再生成补货单");
        }

        // 服务端以配件档案为准重新校验，不信任前端带回的缺口/分区/现存量
        List<Accessory> accessories = accessoryMapper.selectBatchIds(accessoryIds);
        Map<Long, Accessory> accessoryMap = accessories.stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity(), (a, b) -> a, HashMap::new));

        // 请求数量（可空，空则用实时缺口）按配件归并，重复勾选时以最后一个非空值为准
        Map<Long, Integer> requestedQuantityMap = new HashMap<>();
        for (ReplenishCreateDTO.ReplenishItemCreateDTO item : dto.getItems()) {
            if (item.getAccessoryId() != null && item.getReplenishQuantity() != null) {
                requestedQuantityMap.put(item.getAccessoryId(), item.getReplenishQuantity());
            }
        }

        // 分区名称批量装配（分区标签被删的悬挂分区按未分配分区展示，汇总时也归入未分配）
        Set<Long> zoneTagIds = accessories.stream()
                .map(Accessory::getZoneTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = loadZoneTagMap(zoneTagIds);

        List<ReplenishOrderItem> items = new ArrayList<>();
        int totalQuantity = 0;
        for (Long accessoryId : accessoryIds) {
            Accessory accessory = accessoryMap.get(accessoryId);
            if (accessory == null) {
                // 未删除配件被 selectBatchIds（@TableLogic）过滤：不存在或已删除，不允许进入补货单
                throw new RuntimeException("勾选的配件不存在或已删除，请刷新台账后重新勾选");
            }
            if (accessory.getSafetyStock() == null) {
                throw new RuntimeException(String.format(
                        "配件「%s」未设置安全库存下限，不在补货范围内", accessory.getAccessoryName()));
            }
            int stock = accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity();
            int gap = accessory.getSafetyStock() - stock;
            if (gap <= 0) {
                // 台账实时计算，勾选后可能已被核销反冲之外的入库补齐：现存不再低于下限即拒绝
                throw new RuntimeException(String.format(
                        "配件「%s」现存量已不低于安全库存下限，请刷新台账后重新勾选",
                        accessory.getAccessoryName()));
            }
            if (accessory.getReplenishOrderId() != null) {
                throw new RuntimeException(String.format(
                        "配件「%s」已在补货单「%s」待补中，不能重复生成补货单",
                        accessory.getAccessoryName(), accessory.getReplenishOrderNo()));
            }

            Integer requestedQuantity = requestedQuantityMap.get(accessoryId);
            int replenishQuantity = requestedQuantity == null ? gap : requestedQuantity;
            if (requestedQuantity != null && requestedQuantity > gap) {
                // 补货只需补到下限：超出缺口的数量没有台账依据，拒绝以免过量采购
                throw new RuntimeException(String.format(
                        "配件「%s」补货数量不能超过缺口 %d 件", accessory.getAccessoryName(), gap));
            }

            Long zoneTagId = resolveEffectiveZoneId(accessory, zoneTagMap);
            boolean unassigned = zoneTagId == null;
            ReplenishOrderItem item = new ReplenishOrderItem();
            item.setAccessoryId(accessoryId);
            item.setAccessoryName(accessory.getAccessoryName());
            item.setModel(accessory.getModel());
            item.setSpecUnit(accessory.getSpecUnit());
            item.setZoneTagId(zoneTagId);
            item.setZoneName(unassigned ? UNASSIGNED_ZONE_NAME : zoneTagMap.get(zoneTagId).getTagName());
            item.setUnassignedZone(unassigned ? 1 : 0);
            item.setStockQuantity(stock);
            item.setSafetyStock(accessory.getSafetyStock());
            item.setGapQuantity(gap);
            item.setReplenishQuantity(replenishQuantity);
            item.setAccessoryDeleted(0);
            items.add(item);
            totalQuantity += replenishQuantity;
        }

        ReplenishOrder order = new ReplenishOrder();
        order.setStatus(0);
        order.setItemCount(items.size());
        order.setTotalQuantity(totalQuantity);
        insertWithUniqueOrderNo(order);

        for (ReplenishOrderItem item : items) {
            item.setReplenishId(order.getId());
            replenishOrderItemMapper.insert(item);
        }
        return order.getId();
    }

    @Override
    public ReplenishOrderDetailVO getDetailById(Long id) {
        ReplenishOrder order = getRequiredOrder(id);
        List<ReplenishOrderItem> items = listItemsByOrderId(id);

        // 配件当前状态以档案为准：提交前被删除、或已被其他补货单占用，都要在详情里实时标出来
        Map<Long, Accessory> currentMap = loadAccessoriesIncludingDeleted(
                items.stream().map(ReplenishOrderItem::getAccessoryId).collect(Collectors.toSet()));

        List<ReplenishOrderItemVO> itemVOs = new ArrayList<>();
        int conflictCount = 0;
        for (ReplenishOrderItem item : items) {
            Accessory current = currentMap.get(item.getAccessoryId());
            boolean deleted = current == null || (current.getDeleted() != null && current.getDeleted() == 1);
            boolean conflict = !deleted && current.getReplenishOrderId() != null
                    && !current.getReplenishOrderId().equals(id);
            if (conflict || deleted) {
                conflictCount++;
            }
            itemVOs.add(buildItemVO(item, deleted, conflict));
        }
        // 分区汇总与明细同源：直接按明细快照分区分组求和，刷新后小计与合计不漂移
        List<ReplenishZoneSummaryVO> zoneSummaries = buildZoneSummaries(items);

        ReplenishOrderDetailVO detail = new ReplenishOrderDetailVO();
        detail.setHeader(buildHeaderVO(order, zoneSummaries.size()));
        detail.setItems(itemVOs);
        detail.setZoneSummaries(zoneSummaries);
        detail.setConflictCount(conflictCount);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItems(Long id, ReplenishItemDTO dto) {
        ReplenishOrder order = getRequiredOrder(id);
        ensureDraft(order);

        List<ReplenishOrderItem> items = listItemsByOrderId(id);
        Map<Long, ReplenishOrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(ReplenishOrderItem::getId, Function.identity()));

        for (ReplenishItemDTO.ReplenishQuantityDTO quantityDTO : dto.getItems()) {
            ReplenishOrderItem item = quantityDTO.getItemId() == null
                    ? null : itemMap.get(quantityDTO.getItemId());
            if (item == null) {
                throw new RuntimeException("补货明细不存在或不属于本补货单，请刷新后重试");
            }
            // 补货数量不得超过生成时缺口：缺口是补到下限所需的上限，草稿调大调小都以它为界
            if (quantityDTO.getReplenishQuantity() > safeGap(item)) {
                throw new RuntimeException(String.format(
                        "配件「%s」补货数量不能超过缺口 %d 件",
                        item.getAccessoryName(), safeGap(item)));
            }
            UpdateWrapper<ReplenishOrderItem> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", item.getId())
                    .eq("replenish_id", id)
                    .set("replenish_quantity", quantityDTO.getReplenishQuantity());
            replenishOrderItemMapper.update(null, updateWrapper);
            item.setReplenishQuantity(quantityDTO.getReplenishQuantity());
        }

        // 头表合计随草稿调整实时回写，列表页“补货件数合计”、详情分区汇总同源
        int totalQuantity = items.stream()
                .mapToInt(item -> item.getReplenishQuantity() == null ? 0 : item.getReplenishQuantity())
                .sum();
        UpdateWrapper<ReplenishOrder> headerUpdate = new UpdateWrapper<>();
        headerUpdate.eq("id", id)
                .eq("status", 0)
                .set("total_quantity", totalQuantity);
        baseMapper.update(null, headerUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ReplenishOrder order = getRequiredOrder(id);
        ensureDraft(order);

        List<ReplenishOrderItem> items = listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new RuntimeException("补货单没有明细，无法提交");
        }

        // 先做只读预检，给出明确业务原因；真正的并发兜底在下面的行级条件更新
        Map<Long, Accessory> currentMap = loadAccessoriesIncludingDeleted(
                items.stream().map(ReplenishOrderItem::getAccessoryId).collect(Collectors.toSet()));
        for (ReplenishOrderItem item : items) {
            Accessory current = currentMap.get(item.getAccessoryId());
            if (current == null || (current.getDeleted() != null && current.getDeleted() == 1)) {
                throw new RuntimeException(String.format(
                        "配件「%s」已删除，请从补货单删除该行后再提交", item.getAccessoryName()));
            }
            if (current.getReplenishOrderId() != null && !current.getReplenishOrderId().equals(id)) {
                throw new RuntimeException(String.format(
                        "配件「%s」已在补货单「%s」待补中，请先作废冲突单或删除本行后再提交",
                        item.getAccessoryName(), current.getReplenishOrderNo()));
            }
        }

        // 行级条件更新抢占待补标记：replenish_order_id IS NULL 保证同一配件不会被两张有效单同时占用；
        // 任一配件抢占失败即整体回滚，不会留下半张单的待补标记
        for (ReplenishOrderItem item : items) {
            int affected = accessoryMapper.markReplenishPending(
                    item.getAccessoryId(), id, order.getReplenishNo(), item.getReplenishQuantity());
            if (affected != 1) {
                throw new RuntimeException(String.format(
                        "配件「%s」已被其他补货单占用或已删除，提交失败，请刷新后重试",
                        item.getAccessoryName()));
            }
        }

        // 条件置为已提交：并发提交只有一个请求成功，失败方事务回滚全部待补回写
        UpdateWrapper<ReplenishOrder> submitWrapper = new UpdateWrapper<>();
        submitWrapper.eq("id", id)
                .eq("status", 0)
                .set("status", 1)
                .set("submit_time", LocalDateTime.now());
        int affected = baseMapper.update(null, submitWrapper);
        if (affected != 1) {
            throw new RuntimeException("该补货单已提交，请勿重复提交");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, ReplenishCancelDTO dto) {
        ReplenishOrder order = getRequiredOrder(id);
        if (order.getStatus() == null || order.getStatus() != 1) {
            // 草稿直接删除即可；已作废单重复作废无意义
            throw new RuntimeException("仅已提交的补货单可以作废，待提交草稿请直接删除");
        }

        // 先清档案待补标记（带 orderId 条件，不误伤后来新单），再锁单状态，同一事务
        accessoryMapper.clearReplenishPending(id);

        UpdateWrapper<ReplenishOrder> cancelWrapper = new UpdateWrapper<>();
        cancelWrapper.eq("id", id)
                .eq("status", 1)
                .set("status", 2)
                .set("cancel_time", LocalDateTime.now())
                .set("cancel_reason", dto == null ? null : dto.getCancelReason());
        int affected = baseMapper.update(null, cancelWrapper);
        if (affected != 1) {
            throw new RuntimeException("该补货单状态已变化，作废失败，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReplenishOrder order = getRequiredOrder(id);
        ensureDraft(order);
        replenishOrderItemMapper.delete(new LambdaQueryWrapper<ReplenishOrderItem>()
                .eq(ReplenishOrderItem::getReplenishId, id));
        removeById(id);
    }

    // -------------------- 辅助方法 --------------------

    private ReplenishOrder getRequiredOrder(Long id) {
        ReplenishOrder order = getById(id);
        if (order == null) {
            throw new RuntimeException("补货单不存在或已被删除");
        }
        return order;
    }

    /** 已提交/已作废补货单数量锁定：不可再调整补货数量，草稿之外也不可删除 */
    private void ensureDraft(ReplenishOrder order) {
        if (order.getStatus() != null && order.getStatus() == 1) {
            throw new RuntimeException("该补货单已提交，补货数量已锁定不可修改");
        }
        if (order.getStatus() != null && order.getStatus() == 2) {
            throw new RuntimeException("该补货单已作废，单据只读不可修改");
        }
    }

    private List<ReplenishOrderItem> listItemsByOrderId(Long orderId) {
        // 按插入顺序（生成时按勾选配件校验顺序）返回，刷新后顺序稳定
        return replenishOrderItemMapper.selectList(new LambdaQueryWrapper<ReplenishOrderItem>()
                .eq(ReplenishOrderItem::getReplenishId, orderId)
                .orderByAsc(ReplenishOrderItem::getId));
    }

    private Map<Long, Accessory> loadAccessoriesIncludingDeleted(Set<Long> accessoryIds) {
        if (accessoryIds == null || accessoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return accessoryMapper.selectAllByIdsIncludingDeleted(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private Map<Long, ZoneTag> loadZoneTagMap(Set<Long> zoneTagIds) {
        if (zoneTagIds == null || zoneTagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return zoneTagMapper.selectBatchIds(zoneTagIds).stream()
                .collect(Collectors.toMap(ZoneTag::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    /**
     * 计算明细行的有效分区：档案 zone_tag_id 为空、或分区标签已被删除（悬挂分区）都视为未分配分区。
     * 返回 null 表示未分配分区
     */
    private Long resolveEffectiveZoneId(Accessory accessory, Map<Long, ZoneTag> zoneTagMap) {
        Long zoneTagId = accessory.getZoneTagId();
        if (zoneTagId == null) {
            return null;
        }
        return zoneTagMap.containsKey(zoneTagId) ? zoneTagId : null;
    }

    private int safeGap(ReplenishOrderItem item) {
        return item.getGapQuantity() == null ? 0 : item.getGapQuantity();
    }

    private ReplenishOrderItemVO buildItemVO(ReplenishOrderItem item, boolean deleted, boolean conflict) {
        ReplenishOrderItemVO vo = new ReplenishOrderItemVO();
        vo.setId(item.getId());
        vo.setAccessoryId(item.getAccessoryId());
        vo.setAccessoryName(item.getAccessoryName());
        vo.setModel(item.getModel());
        vo.setSpecUnit(item.getSpecUnit());
        vo.setZoneTagId(item.getZoneTagId());
        vo.setZoneName(item.getZoneName());
        vo.setUnassignedZone(item.getUnassignedZone() != null && item.getUnassignedZone() == 1);
        vo.setStockQuantity(item.getStockQuantity());
        vo.setSafetyStock(item.getSafetyStock());
        vo.setGapQuantity(safeGap(item));
        vo.setReplenishQuantity(item.getReplenishQuantity());
        vo.setAccessoryDeleted(deleted);
        vo.setPendingConflict(conflict);
        return vo;
    }

    /**
     * 按分区汇总补货件数：同一快照分区的明细合并为一行，求和补货数量并统计配件种数。
     * 顺序与台账一致：分区排序号升序、同排序号按分区名、未分配分区殿后
     */
    private List<ReplenishZoneSummaryVO> buildZoneSummaries(List<ReplenishOrderItem> items) {
        Set<Long> zoneTagIds = items.stream()
                .map(ReplenishOrderItem::getZoneTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ZoneTag> zoneTagMap = loadZoneTagMap(zoneTagIds);

        // 用 LinkedHashMap 保序：先按排序规则排好分区键再聚合
        List<ReplenishOrderItem> sortedItems = items.stream()
                .sorted(Comparator
                        .comparingLong((ReplenishOrderItem item) -> {
                            boolean unassigned = (item.getUnassignedZone() != null && item.getUnassignedZone() == 1)
                                    || item.getZoneTagId() == null;
                            if (unassigned) {
                                return Long.MAX_VALUE;
                            }
                            ZoneTag zoneTag = zoneTagMap.get(item.getZoneTagId());
                            return zoneTag != null && zoneTag.getSortOrder() != null
                                    ? zoneTag.getSortOrder() : Long.MAX_VALUE;
                        })
                        .thenComparing(item -> {
                            if (item.getZoneTagId() == null) {
                                return "";
                            }
                            ZoneTag zoneTag = zoneTagMap.get(item.getZoneTagId());
                            return zoneTag != null && zoneTag.getTagName() != null ? zoneTag.getTagName() : "";
                        })
                        .thenComparing(ReplenishOrderItem::getId))
                .collect(Collectors.toList());

        Map<Long, ReplenishZoneSummaryVO> summaryByZoneKey = new LinkedHashMap<>();
        for (ReplenishOrderItem item : sortedItems) {
            Long key = item.getZoneTagId() == null ? 0L : item.getZoneTagId();
            ReplenishZoneSummaryVO summary = summaryByZoneKey.get(key);
            if (summary == null) {
                summary = new ReplenishZoneSummaryVO();
                summary.setZoneTagId(item.getZoneTagId());
                summary.setZoneName(item.getZoneName());
                summary.setUnassignedZone(item.getUnassignedZone() != null && item.getUnassignedZone() == 1);
                summary.setAccessoryCount(0);
                summary.setTotalQuantity(0);
                summaryByZoneKey.put(key, summary);
            }
            summary.setAccessoryCount(summary.getAccessoryCount() + 1);
            summary.setTotalQuantity(summary.getTotalQuantity()
                    + (item.getReplenishQuantity() == null ? 0 : item.getReplenishQuantity()));
        }
        return new ArrayList<>(summaryByZoneKey.values());
    }

    private ReplenishOrderVO buildHeaderVO(ReplenishOrder order, Integer zoneCount) {
        ReplenishOrderVO vo = new ReplenishOrderVO();
        vo.setId(order.getId());
        vo.setReplenishNo(order.getReplenishNo());
        vo.setStatus(order.getStatus());
        vo.setStatusText(statusText(order.getStatus()));
        vo.setItemCount(order.getItemCount() == null ? 0 : order.getItemCount());
        vo.setTotalQuantity(order.getTotalQuantity() == null ? 0 : order.getTotalQuantity());
        vo.setZoneCount(zoneCount);
        vo.setCancelReason(order.getCancelReason());
        vo.setSubmitTime(order.getSubmitTime());
        vo.setCancelTime(order.getCancelTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "待提交";
        }
        switch (status) {
            case 1:
                return "已提交";
            case 2:
                return "已作废";
            default:
                return "待提交";
        }
    }

    /** 按补货单 ID 批量统计涉及分区种数（含未分配分区，按明细快照分区去重） */
    private Map<Long, Integer> countZonesByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return replenishOrderItemMapper.selectList(new LambdaQueryWrapper<ReplenishOrderItem>()
                        .in(ReplenishOrderItem::getReplenishId, orderIds)).stream()
                .collect(Collectors.groupingBy(ReplenishOrderItem::getReplenishId,
                        Collectors.collectingAndThen(
                                Collectors.toMap(
                                        item -> item.getZoneTagId() == null ? 0L : item.getZoneTagId(),
                                        item -> 1, (a, b) -> a),
                                Map::size)));
    }

    /**
     * 补货单号：BH + 时间戳 + 3 位随机数；仅单号唯一键（uk_replenish_no）冲突时换随机后缀重试
     */
    private void insertWithUniqueOrderNo(ReplenishOrder order) {
        DuplicateKeyException lastConflict = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            order.setReplenishNo("BH" + LocalDateTime.now().format(ORDER_NO_FORMATTER)
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
            try {
                save(order);
                return;
            } catch (DuplicateKeyException e) {
                lastConflict = e;
            }
        }
        throw new RuntimeException("补货单编号生成冲突，请稍后重试", lastConflict);
    }
}
