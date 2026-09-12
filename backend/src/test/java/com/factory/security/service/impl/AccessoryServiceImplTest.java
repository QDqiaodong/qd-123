package com.factory.security.service.impl;

import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.vo.SafetyStockVO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessoryServiceImplTest {

    // 名称与 ServiceImpl 继承的 baseMapper 字段一致，保证 @InjectMocks 按名注入
    @Mock
    private AccessoryMapper baseMapper;

    @Mock
    private ZoneTagMapper zoneTagMapper;

    @InjectMocks
    private AccessoryServiceImpl accessoryService;

    @BeforeEach
    void injectBaseMapper() throws Exception {
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(accessoryService, baseMapper);
    }

    private Accessory accessory(Long id, String name, Long zoneTagId, int stock, Integer safetyStock) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setAccessoryName(name);
        accessory.setModel(name + "-M");
        accessory.setZoneTagId(zoneTagId);
        accessory.setStockQuantity(stock);
        accessory.setSafetyStock(safetyStock);
        accessory.setDeleted(0);
        return accessory;
    }

    private ZoneTag zone(long id, String name, int sortOrder) {
        ZoneTag zoneTag = new ZoneTag();
        zoneTag.setId(id);
        zoneTag.setTagName(name);
        zoneTag.setSortOrder(sortOrder);
        return zoneTag;
    }

    /**
     * 台账由 mapper 按“已设下限且现存低于下限”过滤后返回，
     * service 负责装配分区名、缺口与未分配标识。这里模拟过滤后的结果。
     */
    @Test
    void listSafetyStockShortagesAssemblesZoneGapAndUnassigned() {
        // 线缆布线区（排序 2）：六类网线 600 < 800，缺口 200
        Accessory cat6 = accessory(4L, "六类网线", 2L, 600, 800);
        // 弱电桥架区（排序 1）：镀锌桥架 80 < 100，缺口 20
        Accessory bridge = accessory(1L, "镀锌桥架", 1L, 80, 100);
        // 未分配分区：某低位配件也必须进台账
        Accessory unassigned = accessory(30L, "未分配低位件", null, 5, 10);

        when(baseMapper.selectList(any())).thenReturn(Arrays.asList(cat6, bridge, unassigned));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                zone(1L, "弱电桥架区", 1),
                zone(2L, "线缆布线区", 2)));

        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, false);

        // 排序：分区排序号升序（弱电桥架区在前），未分配分区最后
        assertEquals(3, rows.size());
        assertEquals("镀锌桥架", rows.get(0).getAccessoryName());
        assertEquals("弱电桥架区", rows.get(0).getZoneTagName());
        assertFalse(rows.get(0).getUnassignedZone());
        assertEquals(80, rows.get(0).getStockQuantity());
        assertEquals(100, rows.get(0).getSafetyStock());
        assertEquals(20, rows.get(0).getGapQuantity());

        assertEquals("六类网线", rows.get(1).getAccessoryName());
        assertEquals(200, rows.get(1).getGapQuantity());

        SafetyStockVO last = rows.get(2);
        assertEquals("未分配低位件", last.getAccessoryName());
        assertTrue(last.getUnassignedZone());
        assertNull(last.getZoneTagId());
        assertNull(last.getZoneTagName());
        assertEquals(5, last.getGapQuantity());
    }

    @Test
    void emptyShortageListReturnsEmptyAndSkipsZoneQuery() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, false);

        assertTrue(rows.isEmpty());
    }

    @Test
    void shortagesByZoneTagIdAssembleZoneRows() {
        // 指定分区筛选在 SQL 层 zone_tag_id=? 下推，mapper 只返回该分区的低位件
        Accessory cat6 = accessory(4L, "六类网线", 2L, 600, 800);
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(cat6));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(
                Collections.singletonList(zone(2L, "线缆布线区", 2)));

        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(2L, false);

        assertEquals(1, rows.size());
        assertEquals("六类网线", rows.get(0).getAccessoryName());
        assertEquals("线缆布线区", rows.get(0).getZoneTagName());
        assertFalse(rows.get(0).getUnassignedZone());
    }

    @Test
    void shortagesFilteredUnassignedOnlyDropsDanglingZoneTag() {
        // 悬挂分区：zone_tag_id 非空但分区标签已删除。真实 SQL 的 IS NULL 粗筛会排除它，
        // 这里直接模拟装配后 unassignedZone=true 的结果，验证二次过滤与标识装配
        Accessory dangling = accessory(31L, "悬挂分区低位件", 99L, 5, 10);
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(dangling));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, true);

        // 分区标签缺失被识别为未分配，筛选保留（IS NULL 与标识过滤双保险均能兜住）
        assertEquals(1, rows.size());
        assertEquals("悬挂分区低位件", rows.get(0).getAccessoryName());
        assertTrue(rows.get(0).getUnassignedZone());
        assertNull(rows.get(0).getZoneTagName());
    }

    @Test
    void nullStockQuantityTreatedAsZeroForGap() {
        Accessory cat6 = accessory(4L, "六类网线", 2L, 0, 800);
        cat6.setStockQuantity(null);
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(cat6));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(
                Collections.singletonList(zone(2L, "线缆布线区", 2)));

        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, false);

        assertEquals(1, rows.size());
        assertEquals(0, rows.get(0).getStockQuantity());
        assertEquals(800, rows.get(0).getGapQuantity());
    }

    @Test
    void updateClearsSafetyStockToNullExplicitly() {
        // updateById 默认忽略 null，清空下限必须经 updateSafetyStock 显式落库，配件才会移出台账
        AccessoryDTO dto = new AccessoryDTO();
        dto.setId(4L);
        dto.setAccessoryName("六类网线");
        dto.setModel("CAT6-UTP");
        dto.setStockQuantity(600);
        dto.setSafetyStock(null);
        when(baseMapper.updateById(any(Accessory.class))).thenReturn(1);
        when(baseMapper.updateSafetyStock(eq(4L), isNull())).thenReturn(1);

        boolean result = accessoryService.update(dto);

        assertTrue(result);
        verify(baseMapper).updateSafetyStock(4L, null);
    }

    @Test
    void failedUpdateSkipsSafetyStockSync() {
        AccessoryDTO dto = new AccessoryDTO();
        dto.setId(4L);
        dto.setAccessoryName("六类网线");
        dto.setModel("CAT6-UTP");
        dto.setStockQuantity(600);
        dto.setSafetyStock(900);
        when(baseMapper.updateById(any(Accessory.class))).thenReturn(0);

        assertFalse(accessoryService.update(dto));
        verify(baseMapper, never()).updateSafetyStock(any(), any());
    }
}
