package com.factory.security.service.impl;

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
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCheckServiceImplTest {

    // 名称与 ServiceImpl 继承的 baseMapper 字段一致，保证 @InjectMocks 按名注入
    @Mock
    private StockCheckMapper baseMapper;

    @Mock
    private StockCheckItemMapper stockCheckItemMapper;

    @Mock
    private AccessoryMapper accessoryMapper;

    @Mock
    private ZoneTagMapper zoneTagMapper;

    @InjectMocks
    private StockCheckServiceImpl stockCheckService;

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // 与 WiringPlanServiceImplTest 相同：按字段名显式注入 ServiceImpl#baseMapper
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(stockCheckService, baseMapper);
    }

    private Accessory accessory(Long id, String name, int stock, int deleted) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setAccessoryName(name);
        accessory.setModel(name + "-M");
        accessory.setStockQuantity(stock);
        accessory.setDeleted(deleted);
        return accessory;
    }

    private StockCheck pendingCheck(Long id, Long zoneTagId, String zoneName) {
        StockCheck check = new StockCheck();
        check.setId(id);
        check.setCheckNo("PD" + id);
        check.setZoneTagId(zoneTagId);
        check.setZoneName(zoneName);
        check.setUnassignedZone(zoneTagId == null ? 1 : 0);
        check.setStatus(0);
        check.setItemCount(2);
        check.setDiffCount(0);
        return check;
    }

    private StockCheckItem item(Long id, Long accessoryId, String name, int book, Integer actual, int deleted) {
        StockCheckItem item = new StockCheckItem();
        item.setId(id);
        item.setCheckId(10L);
        item.setAccessoryId(accessoryId);
        item.setAccessoryName(name);
        item.setModel(name + "-M");
        item.setBookQuantity(book);
        item.setActualQuantity(actual);
        item.setAccessoryDeleted(deleted);
        return item;
    }

    private StockCheckItemDTO.StockCheckActualDTO actualDTO(Long itemId, int quantity) {
        StockCheckItemDTO.StockCheckActualDTO dto = new StockCheckItemDTO.StockCheckActualDTO();
        dto.setItemId(itemId);
        dto.setActualQuantity(quantity);
        return dto;
    }

    @Test
    void createForNormalZoneSnapshotsAccessories() {
        ZoneTag zone = new ZoneTag();
        zone.setId(2L);
        zone.setTagName("线缆布线区");
        when(zoneTagMapper.selectById(2L)).thenReturn(zone);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectByZoneIncludingDeleted(2L))
                .thenReturn(Arrays.asList(accessory(3L, "超五类网线", 1000, 0), accessory(4L, "六类网线", 600, 0)));

        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(2L);
        stockCheckService.create(dto);

        // 头表 1 次 + 明细 2 条，账面快照取开盘时现存量、分区名为快照
        org.mockito.ArgumentCaptor<StockCheck> checkCaptor =
                org.mockito.ArgumentCaptor.forClass(StockCheck.class);
        verify(baseMapper).insert(checkCaptor.capture());
        StockCheck savedCheck = checkCaptor.getValue();
        assertEquals(2L, savedCheck.getZoneTagId());
        assertEquals("线缆布线区", savedCheck.getZoneName());
        assertEquals(0, savedCheck.getStatus());
        assertEquals(2, savedCheck.getItemCount());

        org.mockito.ArgumentCaptor<StockCheckItem> itemCaptor =
                org.mockito.ArgumentCaptor.forClass(StockCheckItem.class);
        verify(stockCheckItemMapper, times(2)).insert(itemCaptor.capture());
        List<StockCheckItem> savedItems = itemCaptor.getAllValues();
        assertEquals(1000, savedItems.get(0).getBookQuantity());
        assertEquals(600, savedItems.get(1).getBookQuantity());
        assertNull(savedItems.get(0).getActualQuantity());
        assertEquals(0, savedItems.get(0).getAccessoryDeleted());
    }

    @Test
    void createForUnassignedZoneQueriesNullZone() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectByZoneIncludingDeleted(isNull()))
                .thenReturn(Collections.singletonList(accessory(9L, "扎带", 400, 0)));

        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(null);
        stockCheckService.create(dto);

        verify(accessoryMapper).selectByZoneIncludingDeleted(isNull());
        verify(stockCheckItemMapper).insert(any(StockCheckItem.class));
    }

    @Test
    void createForEmptyZoneStillOpensCheck() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectByZoneIncludingDeleted(isNull())).thenReturn(Collections.emptyList());

        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(null);
        stockCheckService.create(dto);

        // 空分区也开盘：头表插入，明细 0 条
        verify(baseMapper).insert(any(StockCheck.class));
        verify(stockCheckItemMapper, never()).insert(any(StockCheckItem.class));
    }

    @Test
    void createRejectsWhenPendingCheckExistsForZone() {
        ZoneTag zone = new ZoneTag();
        zone.setId(2L);
        zone.setTagName("线缆布线区");
        when(zoneTagMapper.selectById(2L)).thenReturn(zone);
        when(baseMapper.selectCount(any())).thenReturn(1L);

        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(2L);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> stockCheckService.create(dto));
        assertTrue(ex.getMessage().contains("已存在待确认盘点单"));
        verify(baseMapper, never()).insert(any(StockCheck.class));
    }

    @Test
    void createRejectsUnknownZone() {
        when(zoneTagMapper.selectById(99L)).thenReturn(null);
        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(99L);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> stockCheckService.create(dto));
        assertTrue(ex.getMessage().contains("所选分区不存在"));
    }

    @Test
    void createIncludesDeletedAccessoriesAsSnapshotItems() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(accessoryMapper.selectByZoneIncludingDeleted(isNull()))
                .thenReturn(Arrays.asList(
                        accessory(9L, "扎带", 400, 0),
                        accessory(99L, "旧型号", 10, 1)));

        StockCheckCreateDTO dto = new StockCheckCreateDTO();
        dto.setZoneTagId(null);
        stockCheckService.create(dto);

        // 已删除配件同样带入盘点单（只展示）：2 条明细
        verify(stockCheckItemMapper, times(2)).insert(any(StockCheckItem.class));
    }

    @Test
    void recordItemsUpdatesActualAndDiffCountWithoutTouchingStock() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        StockCheckItem item1 = item(1001L, 3L, "超五类网线", 1000, null, 0);
        StockCheckItem item2 = item(1002L, 4L, "六类网线", 600, null, 0);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(accessory(3L, "超五类网线", 1000, 0), accessory(4L, "六类网线", 600, 0)));

        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(Collections.singletonList(actualDTO(1001L, 980)));
        stockCheckService.recordItems(10L, dto);

        // 实盘落明细，且不触碰库存
        verify(stockCheckItemMapper).update(any(), any());
        verify(accessoryMapper, never()).resetStock(anyLong(), anyInt());
        // 差异 1 种回写头表
        verify(baseMapper).update(any(), any());
    }

    @Test
    void recordItemsSkipsDeletedAccessories() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, null, "未分配分区"));
        StockCheckItem deletedItem = item(1003L, 99L, "旧型号", 10, 5, 1);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Collections.singletonList(deletedItem));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Collections.singletonList(accessory(99L, "旧型号", 10, 1)));

        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(Collections.singletonList(actualDTO(1003L, 8)));
        stockCheckService.recordItems(10L, dto);

        // 已删除配件：不实盘更新、不回写库存
        verify(stockCheckItemMapper, never()).update(org.mockito.ArgumentMatchers.any(), any());
        verify(accessoryMapper, never()).resetStock(anyLong(), anyInt());
    }

    @Test
    void recordItemsMarksAccessoryDeletedAfterOpeningAndSkipsIt() {
        // 开盘时配件正常（明细快照 deleted=0、无实盘值），登记前配件才被删除：
        // 自定义 SQL 查不到该配件（currentMap 不含该ID）
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        StockCheckItem item = item(1001L, 3L, "超五类网线", 1000, null, 0);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any())).thenReturn(Collections.emptyList());

        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(Collections.singletonList(actualDTO(1001L, 980)));
        stockCheckService.recordItems(10L, dto);

        // 不写实盘值，而是把明细标记为已删除并清空实盘；不回写库存
        verify(stockCheckItemMapper).update(any(), any());
        assertEquals(1, item.getAccessoryDeleted());
        assertNull(item.getActualQuantity());
        verify(accessoryMapper, never()).resetStock(anyLong(), anyInt());
    }

    @Test
    void recordItemsRejectsForConfirmedCheck() {
        StockCheck confirmed = pendingCheck(10L, 2L, "线缆布线区");
        confirmed.setStatus(1);
        when(baseMapper.selectById(10L)).thenReturn(confirmed);

        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(Collections.singletonList(actualDTO(1001L, 1)));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockCheckService.recordItems(10L, dto));
        assertTrue(ex.getMessage().contains("已确认"));
        verify(stockCheckItemMapper, never()).update(any(), any());
    }

    @Test
    void confirmResetsStockOnceAndLocksCheck() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        StockCheckItem item1 = item(1001L, 3L, "超五类网线", 1000, 980, 0);
        StockCheckItem item2 = item(1002L, 4L, "六类网线", 600, 650, 0);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(accessory(3L, "超五类网线", 1000, 0), accessory(4L, "六类网线", 600, 0)));
        when(accessoryMapper.resetStock(anyLong(), anyInt())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        stockCheckService.confirm(10L, new StockCheckConfirmDTO());

        // 按实盘数一次性回写两条配件库存（盘亏 980、盘盈 650）
        verify(accessoryMapper).resetStock(3L, 980);
        verify(accessoryMapper).resetStock(4L, 650);
        verify(baseMapper).update(any(), any());
    }

    @Test
    void confirmSkipsDeletedAccessoryAndDoesNotResetItsStock() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, null, "未分配分区"));
        StockCheckItem normal = item(1001L, 9L, "扎带", 400, 400, 0);
        StockCheckItem deleted = item(1003L, 99L, "旧型号", 10, 10, 1);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(normal, deleted));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(accessory(9L, "扎带", 400, 0), accessory(99L, "旧型号", 10, 1)));
        when(accessoryMapper.resetStock(anyLong(), anyInt())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        stockCheckService.confirm(10L, new StockCheckConfirmDTO());

        // 已删除配件只展示不回写：只回写扎带 1 条
        verify(accessoryMapper).resetStock(9L, 400);
        verify(accessoryMapper, never()).resetStock(eq(99L), anyInt());
    }

    @Test
    void confirmRejectsWhenAnyNormalItemUnrecorded() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        StockCheckItem recorded = item(1001L, 3L, "超五类网线", 1000, 980, 0);
        StockCheckItem unrecorded = item(1002L, 4L, "六类网线", 600, null, 0);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(recorded, unrecorded));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(accessory(3L, "超五类网线", 1000, 0), accessory(4L, "六类网线", 600, 0)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockCheckService.confirm(10L, new StockCheckConfirmDTO()));
        assertTrue(ex.getMessage().contains("尚未登记实盘数"));
        verify(accessoryMapper, never()).resetStock(anyLong(), anyInt());
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void confirmAllowsDeletedItemWithoutActualQuantity() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, null, "未分配分区"));
        StockCheckItem normal = item(1001L, 9L, "扎带", 400, 400, 0);
        StockCheckItem deleted = item(1003L, 99L, "旧型号", 10, null, 1);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(normal, deleted));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(accessory(9L, "扎带", 400, 0), accessory(99L, "旧型号", 10, 1)));
        when(accessoryMapper.resetStock(anyLong(), anyInt())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        stockCheckService.confirm(10L, new StockCheckConfirmDTO());

        verify(accessoryMapper).resetStock(9L, 400);
        verify(accessoryMapper, never()).resetStock(eq(99L), anyInt());
    }

    @Test
    void confirmRejectsAlreadyConfirmedCheck() {
        StockCheck confirmed = pendingCheck(10L, 2L, "线缆布线区");
        confirmed.setStatus(1);
        when(baseMapper.selectById(10L)).thenReturn(confirmed);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockCheckService.confirm(10L, new StockCheckConfirmDTO()));
        assertTrue(ex.getMessage().contains("已确认"));
        verify(accessoryMapper, never()).resetStock(anyLong(), anyInt());
    }

    @Test
    void detailComputesDiffAndMarksDeleted() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(
                item(1001L, 3L, "超五类网线", 1000, 980, 0),
                item(1002L, 4L, "六类网线", 600, null, 0),
                item(1003L, 99L, "旧型号", 10, null, 1)));
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Arrays.asList(
                        accessory(3L, "超五类网线", 1000, 0),
                        accessory(4L, "六类网线", 600, 0),
                        accessory(99L, "旧型号", 10, 1)));

        StockCheckDetailVO detail = stockCheckService.getDetailById(10L);

        assertEquals(1, detail.getRecordedCount());
        assertEquals(1, detail.getDiffCount());
        assertEquals(0, detail.getGainCount());
        assertEquals(1, detail.getLossCount());
        assertEquals(-20, detail.getTotalDiffQuantity());
        assertEquals(1, detail.getItems().stream().filter(item -> Boolean.TRUE.equals(item.getAccessoryDeleted())).count());
        StockCheckItemVO lossItem = detail.getItems().get(0);
        assertEquals(-20, lossItem.getDiffQuantity());
        assertEquals("loss", lossItem.getDiffType());
        StockCheckItemVO deletedItem = detail.getItems().get(2);
        assertTrue(deletedItem.getAccessoryDeleted());
        assertEquals("deleted", deletedItem.getDiffType());
        assertNull(deletedItem.getDiffQuantity());
    }

    @Test
    void deleteOnlyAllowedForPendingCheck() {
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        when(baseMapper.deleteById(10L)).thenReturn(1);

        stockCheckService.delete(10L);

        verify(stockCheckItemMapper).delete(any());
        verify(baseMapper).deleteById(10L);
    }

    @Test
    void deleteRejectedForConfirmedCheck() {
        StockCheck confirmed = pendingCheck(10L, 2L, "线缆布线区");
        confirmed.setStatus(1);
        when(baseMapper.selectById(10L)).thenReturn(confirmed);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> stockCheckService.delete(10L));
        assertTrue(ex.getMessage().contains("已确认"));
        verify(stockCheckItemMapper, never()).delete(any());
        verify(baseMapper, never()).deleteById(anyLong());
    }

    @Test
    void itemDeletedAfterOpeningBecomesSkippedInConfirm() {
        // 开盘后配件才被删除：明细快照 deleted=0，但档案当前查不到（selectAllByIdsIncludingDeleted 不含该ID）
        when(baseMapper.selectById(10L)).thenReturn(pendingCheck(10L, 2L, "线缆布线区"));
        StockCheckItem item1 = item(1001L, 3L, "超五类网线", 1000, 1000, 0);
        StockCheckItem item2 = item(1002L, 4L, "六类网线", 600, 600, 0);
        when(stockCheckItemMapper.selectList(any())).thenReturn(Arrays.asList(item1, item2));
        // 物理缺失：自定义 SQL 仍查不到第二个配件
        when(accessoryMapper.selectAllByIdsIncludingDeleted(any()))
                .thenReturn(Collections.singletonList(accessory(3L, "超五类网线", 1000, 0)));
        when(accessoryMapper.resetStock(anyLong(), anyInt())).thenReturn(1);
        when(baseMapper.update(any(), any())).thenReturn(1);

        stockCheckService.confirm(10L, new StockCheckConfirmDTO());

        verify(accessoryMapper).resetStock(3L, 1000);
        // 开盘后被删除的配件不回写
        verify(accessoryMapper, never()).resetStock(eq(4L), anyInt());
    }

    @Test
    void detailForMissingCheckThrows() {
        when(baseMapper.selectById(404L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockCheckService.getDetailById(404L));
        assertTrue(ex.getMessage().contains("盘点单不存在"));
    }
}
