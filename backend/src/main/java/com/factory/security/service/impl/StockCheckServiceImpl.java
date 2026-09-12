package com.factory.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.StockCheck;
import com.factory.security.entity.StockCheckItem;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.StockCheckItemMapper;
import com.factory.security.mapper.StockCheckMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.StockCheckService;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckItemVO;
import com.factory.security.vo.StockCheckVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockCheckServiceImpl extends ServiceImpl<StockCheckMapper, StockCheck> implements StockCheckService {

    /** 未分配分区的展示名称与开盘快照名 */
    private static final String UNASSIGNED_ZONE_NAME = "未分配分区";

    private static final DateTimeFormatter CHECK_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private StockCheckItemMapper stockCheckItemMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Override
    public Page<StockCheckVO> page(Integer pageNum, Integer pageSize, Integer status,
                                   Long zoneTagId, boolean unassigned) {
        Page<StockCheck> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<StockCheck> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(StockCheck::getStatus, status);
        }
        if (unassigned) {
            wrapper.isNull(StockCheck::getZoneTagId);
        } else if (zoneTagId != null) {
            wrapper.eq(StockCheck::getZoneTagId, zoneTagId);
        }
        wrapper.orderByDesc(StockCheck::getCreateTime);
        wrapper.orderByDesc(StockCheck::getId);
        Page<StockCheck> checkPage = page(page, wrapper);

        List<StockCheck> checks = checkPage.getRecords();
        Map<Long, Integer> recordedCountMap = countRecordedByCheckIds(
                checks.stream().map(StockCheck::getId).collect(Collectors.toList()));

        Page<StockCheckVO> voPage = new Page<>(checkPage.getCurrent(), checkPage.getSize(), checkPage.getTotal());
        voPage.setRecords(checks.stream()
                .map(check -> buildHeaderVO(check, recordedCountMap.getOrDefault(check.getId(), 0)))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(StockCheckCreateDTO dto) {
        boolean unassigned = dto.getZoneTagId() == null;
        String zoneName;
        if (unassigned) {
            zoneName = UNASSIGNED_ZONE_NAME;
        } else {
            ZoneTag zoneTag = zoneTagMapper.selectById(dto.getZoneTagId());
            if (zoneTag == null) {
                throw new RuntimeException("所选分区不存在或已删除，无法开盘");
            }
            zoneName = zoneTag.getTagName();
        }

        // 同分区待确认单判重：存在则直接拒绝；并发开盘的最终兜底是
        // 数据库生成列唯一索引 uk_pending_zone（status=0 时 pending_zone_key=COALESCE(zone_tag_id,0)）
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<StockCheck>()
                .eq(StockCheck::getStatus, 0)
                .eq(unassigned, StockCheck::getUnassignedZone, 1)
                .eq(!unassigned, StockCheck::getZoneTagId, dto.getZoneTagId())
                .isNull(unassigned, StockCheck::getZoneTagId));
        if (count != null && count > 0) {
            throw new RuntimeException(String.format(
                    "分区「%s」已存在待确认盘点单，请先登记并确认或删除后再开盘", zoneName));
        }

        // 空分区、未分配分区均允许开盘：分区内无配件时明细为空，单据仍保留登记入口与留档
        List<Accessory> accessories = accessoryMapper.selectByZoneIncludingDeleted(dto.getZoneTagId());

        StockCheck check = new StockCheck();
        check.setZoneTagId(dto.getZoneTagId());
        check.setZoneName(zoneName);
        check.setUnassignedZone(unassigned ? 1 : 0);
        check.setStatus(0);
        check.setItemCount(accessories.size());
        check.setDiffCount(0);
        try {
            insertWithUniqueCheckNo(check);
        } catch (DuplicateKeyException e) {
            // 行锁判重后仍并发提交：uk_pending_zone 唯一索引兜底，抛出明确业务提示
            throw new RuntimeException(String.format(
                    "分区「%s」已存在待确认盘点单，请先登记并确认或删除后再开盘", zoneName));
        }

        for (Accessory accessory : accessories) {
            StockCheckItem item = new StockCheckItem();
            item.setCheckId(check.getId());
            item.setAccessoryId(accessory.getId());
            item.setAccessoryName(accessory.getAccessoryName());
            item.setModel(accessory.getModel());
            item.setSpecUnit(accessory.getSpecUnit());
            item.setBookQuantity(accessory.getStockQuantity() == null ? 0 : accessory.getStockQuantity());
            item.setActualQuantity(null);
            item.setAccessoryDeleted(accessory.getDeleted() != null && accessory.getDeleted() == 1 ? 1 : 0);
            stockCheckItemMapper.insert(item);
        }
        return check.getId();
    }

    @Override
    public StockCheckDetailVO getDetailById(Long id) {
        StockCheck check = getRequiredCheck(id);
        List<StockCheckItem> items = listItemsByCheckId(id);

        // 已删除状态以配件档案当前状态为准（开盘后被删除也要立即转为“只展示不回写”），
        // 开盘时已删除的快照同样保持只展示
        Map<Long, Accessory> currentMap = loadAccessoriesIncludingDeleted(
                items.stream().map(StockCheckItem::getAccessoryId).collect(Collectors.toSet()));

        List<StockCheckItemVO> itemVOs = new ArrayList<>();
        int recorded = 0;
        int diff = 0;
        int gain = 0;
        int loss = 0;
        int totalDiffQuantity = 0;
        for (StockCheckItem item : items) {
            Accessory current = currentMap.get(item.getAccessoryId());
            boolean deleted = (item.getAccessoryDeleted() != null && item.getAccessoryDeleted() == 1)
                    || current == null
                    || (current.getDeleted() != null && current.getDeleted() == 1);
            StockCheckItemVO vo = buildItemVO(item, deleted);
            itemVOs.add(vo);
            if (deleted) {
                continue;
            }
            if (item.getActualQuantity() != null) {
                recorded++;
                int diffQuantity = item.getActualQuantity() - safeBook(item);
                totalDiffQuantity += diffQuantity;
                if (diffQuantity != 0) {
                    diff++;
                    if (diffQuantity > 0) {
                        gain++;
                    } else {
                        loss++;
                    }
                }
            }
        }

        StockCheckDetailVO detail = new StockCheckDetailVO();
        detail.setHeader(buildHeaderVO(check, recorded));
        detail.setItems(itemVOs);
        detail.setRecordedCount(recorded);
        detail.setDiffCount(diff);
        detail.setGainCount(gain);
        detail.setLossCount(loss);
        detail.setTotalDiffQuantity(totalDiffQuantity);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordItems(Long id, StockCheckItemDTO dto) {
        StockCheck check = getRequiredCheck(id);
        ensurePending(check);

        List<StockCheckItem> items = listItemsByCheckId(id);
        Map<Long, StockCheckItem> itemMap = items.stream()
                .collect(Collectors.toMap(StockCheckItem::getId, Function.identity()));
        // 配件当前是否已删除以档案为准（MyBatis-Plus 查不到即已删除），
        // 开盘后被删除的配件立即转为“只展示不回写”
        Map<Long, Accessory> currentMap = loadAccessoriesIncludingDeleted(
                items.stream().map(StockCheckItem::getAccessoryId).collect(Collectors.toSet()));

        for (StockCheckItemDTO.StockCheckActualDTO actual : dto.getItems()) {
            StockCheckItem item = actual.getItemId() == null ? null : itemMap.get(actual.getItemId());
            if (item == null) {
                throw new RuntimeException("盘点明细不存在或不属于本盘点单，请刷新后重试");
            }
            boolean snapshotDeleted = item.getAccessoryDeleted() != null && item.getAccessoryDeleted() == 1;
            boolean currentlyDeleted = isCurrentlyDeleted(currentMap.get(item.getAccessoryId()));
            if (currentlyDeleted) {
                // 已删除配件只展示：实盘值不落库；同时把明细删除标记补齐为 1，供列表/后续确认直接判断
                if (!snapshotDeleted) {
                    LambdaUpdateWrapper<StockCheckItem> markDeleted = new LambdaUpdateWrapper<>();
                    markDeleted.eq(StockCheckItem::getId, item.getId())
                            .eq(StockCheckItem::getCheckId, id)
                            .set(StockCheckItem::getAccessoryDeleted, 1)
                            .set(StockCheckItem::getActualQuantity, null);
                    stockCheckItemMapper.update(null, markDeleted);
                }
                // 同步内存状态，避免随后 countDiff 把该配件计入差异
                item.setAccessoryDeleted(1);
                item.setActualQuantity(null);
                continue;
            }
            LambdaUpdateWrapper<StockCheckItem> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(StockCheckItem::getId, item.getId())
                    .eq(StockCheckItem::getCheckId, id)
                    .set(StockCheckItem::getActualQuantity, actual.getActualQuantity());
            stockCheckItemMapper.update(null, updateWrapper);
            item.setActualQuantity(actual.getActualQuantity());
        }

        // 差异种数实时回写头表，列表页与详情页口径一致，刷新后不变
        int diffCount = countDiff(items, currentMap);
        LambdaUpdateWrapper<StockCheck> headerUpdate = new LambdaUpdateWrapper<>();
        headerUpdate.eq(StockCheck::getId, id)
                .eq(StockCheck::getStatus, 0)
                .set(StockCheck::getDiffCount, diffCount);
        baseMapper.update(null, headerUpdate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, StockCheckConfirmDTO dto) {
        StockCheck check = getRequiredCheck(id);
        ensurePending(check);

        List<StockCheckItem> items = listItemsByCheckId(id);
        Map<Long, Accessory> currentMap = loadAccessoriesIncludingDeleted(
                items.stream().map(StockCheckItem::getAccessoryId).collect(Collectors.toSet()));

        int diffCount = 0;
        for (StockCheckItem item : items) {
            boolean deleted = (item.getAccessoryDeleted() != null && item.getAccessoryDeleted() == 1)
                    || isCurrentlyDeleted(currentMap.get(item.getAccessoryId()));
            if (deleted) {
                // 已删除配件只展示，不回写库存
                continue;
            }
            if (item.getActualQuantity() == null) {
                throw new RuntimeException(String.format(
                        "配件「%s」尚未登记实盘数，请登记全部配件后再确认", item.getAccessoryName()));
            }
            // 实盘数直接覆盖账面（盘盈盘亏都允许）；条件 deleted=0 防止回写到已删除配件。
            // 返回 0 行说明配件在本次加载后被并发删除，跳过且不计差异（不回写）
            int affected = accessoryMapper.resetStock(item.getAccessoryId(), item.getActualQuantity());
            if (affected == 1 && !item.getActualQuantity().equals(safeBook(item))) {
                diffCount++;
            }
        }

        // 条件置为已确认：并发确认时只有一个请求成功，失败方事务回滚全部库存回写
        LambdaUpdateWrapper<StockCheck> confirmWrapper = new LambdaUpdateWrapper<>();
        confirmWrapper.eq(StockCheck::getId, id)
                .eq(StockCheck::getStatus, 0)
                .set(StockCheck::getStatus, 1)
                .set(StockCheck::getDiffCount, diffCount)
                .set(StockCheck::getConfirmTime, LocalDateTime.now())
                .set(StockCheck::getConfirmRemark, dto == null ? null : dto.getConfirmRemark());
        int affected = baseMapper.update(null, confirmWrapper);
        if (affected != 1) {
            throw new RuntimeException("该盘点单已确认，库存已回写，请勿重复确认");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StockCheck check = getRequiredCheck(id);
        ensurePending(check);
        stockCheckItemMapper.delete(new LambdaQueryWrapper<StockCheckItem>()
                .eq(StockCheckItem::getCheckId, id));
        removeById(id);
    }

    // -------------------- 辅助方法 --------------------

    private StockCheck getRequiredCheck(Long id) {
        StockCheck check = getById(id);
        if (check == null) {
            throw new RuntimeException("盘点单不存在或已被删除");
        }
        return check;
    }

    /** 已确认盘点单只读：不可再登记实盘数、不可确认、不可删除 */
    private void ensurePending(StockCheck check) {
        if (check.getStatus() != null && check.getStatus() == 1) {
            throw new RuntimeException("该盘点单已确认并回写库存，单据已锁定不可修改");
        }
    }

    private List<StockCheckItem> listItemsByCheckId(Long checkId) {
        // 按插入顺序（开盘时按配件名称排序）返回，刷新后顺序稳定
        return stockCheckItemMapper.selectList(new LambdaQueryWrapper<StockCheckItem>()
                .eq(StockCheckItem::getCheckId, checkId)
                .orderByAsc(StockCheckItem::getId));
    }

    private Map<Long, Accessory> loadAccessoriesIncludingDeleted(Set<Long> accessoryIds) {
        if (accessoryIds == null || accessoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return accessoryMapper.selectAllByIdsIncludingDeleted(accessoryIds).stream()
                .collect(Collectors.toMap(Accessory::getId, Function.identity(), (a, b) -> a, HashMap::new));
    }

    private boolean isCurrentlyDeleted(Accessory current) {
        return current == null || (current.getDeleted() != null && current.getDeleted() == 1);
    }

    private int safeBook(StockCheckItem item) {
        return item.getBookQuantity() == null ? 0 : item.getBookQuantity();
    }

    private int countDiff(List<StockCheckItem> items, Map<Long, Accessory> currentMap) {
        int count = 0;
        for (StockCheckItem item : items) {
            boolean deleted = (item.getAccessoryDeleted() != null && item.getAccessoryDeleted() == 1)
                    || isCurrentlyDeleted(currentMap.get(item.getAccessoryId()));
            if (!deleted && item.getActualQuantity() != null
                    && !item.getActualQuantity().equals(safeBook(item))) {
                count++;
            }
        }
        return count;
    }

    private StockCheckItemVO buildItemVO(StockCheckItem item, boolean deleted) {
        StockCheckItemVO vo = new StockCheckItemVO();
        vo.setId(item.getId());
        vo.setAccessoryId(item.getAccessoryId());
        vo.setAccessoryName(item.getAccessoryName());
        vo.setModel(item.getModel());
        vo.setSpecUnit(item.getSpecUnit());
        vo.setBookQuantity(safeBook(item));
        vo.setActualQuantity(item.getActualQuantity());
        vo.setAccessoryDeleted(deleted);
        if (deleted) {
            vo.setRecorded(false);
            vo.setDiffQuantity(null);
            vo.setDiffType("deleted");
        } else if (item.getActualQuantity() == null) {
            vo.setRecorded(false);
            vo.setDiffQuantity(null);
            vo.setDiffType("unrecorded");
        } else {
            int diffQuantity = item.getActualQuantity() - safeBook(item);
            vo.setRecorded(true);
            vo.setDiffQuantity(diffQuantity);
            vo.setDiffType(diffQuantity > 0 ? "gain" : diffQuantity < 0 ? "loss" : "even");
        }
        return vo;
    }

    private StockCheckVO buildHeaderVO(StockCheck check, Integer recordedCount) {
        StockCheckVO vo = new StockCheckVO();
        vo.setId(check.getId());
        vo.setCheckNo(check.getCheckNo());
        vo.setZoneTagId(check.getZoneTagId());
        vo.setZoneName(check.getZoneName());
        vo.setUnassignedZone(check.getUnassignedZone() != null && check.getUnassignedZone() == 1);
        vo.setStatus(check.getStatus());
        vo.setStatusText(check.getStatus() != null && check.getStatus() == 1 ? "已确认" : "待确认");
        vo.setItemCount(check.getItemCount() == null ? 0 : check.getItemCount());
        vo.setRecordedCount(recordedCount);
        vo.setDiffCount(check.getDiffCount() == null ? 0 : check.getDiffCount());
        vo.setConfirmRemark(check.getConfirmRemark());
        vo.setConfirmTime(check.getConfirmTime());
        vo.setCreateTime(check.getCreateTime());
        return vo;
    }

    /** 按盘点单 ID 批量统计已登记实盘数的明细种数（已删除配件即使有值也不计入登记进度） */
    private Map<Long, Integer> countRecordedByCheckIds(List<Long> checkIds) {
        if (checkIds == null || checkIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return stockCheckItemMapper.selectList(new LambdaQueryWrapper<StockCheckItem>()
                        .in(StockCheckItem::getCheckId, checkIds)
                        .isNotNull(StockCheckItem::getActualQuantity)
                        .eq(StockCheckItem::getAccessoryDeleted, 0)).stream()
                .collect(Collectors.groupingBy(StockCheckItem::getCheckId, Collectors.summingInt(item -> 1)));
    }

    /**
     * 盘点单号：PD + 时间戳 + 3 位随机数；仅单号唯一键（uk_check_no）冲突时换随机后缀重试，
     * 同分区待确认唯一键（uk_pending_zone）冲突不在此吞掉，交由上层转成业务提示
     */
    private void insertWithUniqueCheckNo(StockCheck check) {
        DuplicateKeyException lastCheckNoConflict = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            check.setCheckNo("PD" + LocalDateTime.now().format(CHECK_NO_FORMATTER)
                    + String.format("%03d", ThreadLocalRandom.current().nextInt(1000)));
            try {
                save(check);
                return;
            } catch (DuplicateKeyException e) {
                if (isCheckNoConflict(e)) {
                    lastCheckNoConflict = e;
                    continue;
                }
                // uk_pending_zone 冲突等其他唯一冲突直接抛出，由 create() 转为分区重复开盘提示
                throw e;
            }
        }
        throw new RuntimeException("盘点单编号生成冲突，请稍后重试", lastCheckNoConflict);
    }

    /** MySQL 唯一冲突消息形如 "Duplicate entry ... for key 'stock_check.uk_check_no'" */
    private boolean isCheckNoConflict(DuplicateKeyException e) {
        Throwable mostSpecific = e.getMostSpecificCause();
        String message = mostSpecific != null ? mostSpecific.getMessage() : e.getMessage();
        return message != null && message.contains("uk_check_no");
    }
}
