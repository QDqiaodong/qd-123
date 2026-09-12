package com.factory.security.service.impl;

import com.factory.security.dto.AccessoryDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.AccessoryService;
import com.factory.security.vo.SafetyStockVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全库存台账链路集成测试（H2 MySQL 兼容模式，真实 SQL 落库）：
 * 仅已设下限且现存量低于下限的正常配件进台账；未设下限、已删除的不进；
 * 未分配分区低位不漏；改下限或现存量后实时重算，条数与缺口与档案一致。
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:safetystock;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:schema-it.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class SafetyStockShortageIT {

    @Autowired
    private AccessoryService accessoryService;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    private Long cableZoneId;

    @BeforeEach
    void seed() {
        ZoneTag cableZone = new ZoneTag();
        cableZone.setTagName("线缆布线区");
        cableZone.setTagCode("ZONE-CABLE-SS");
        cableZone.setSortOrder(2);
        zoneTagMapper.insert(cableZone);
        cableZoneId = cableZone.getId();

        // 六类网线：下限 800，现存 600，低于下限，在分区内
        insertAccessory("六类网线", "CAT6-SS", cableZoneId, 600, 800, 0);
        // 超五类网线：下限 800，现存 1000，充足，不进台账
        insertAccessory("超五类网线", "CAT5e-SS", cableZoneId, 1000, 800, 0);
        // RVV电源线：未设下限，哪怕现存很低也不进台账
        insertAccessory("RVV电源线", "RVV-SS", cableZoneId, 1, null, 0);
        // 已删除低位配件：不进台账
        insertAccessory("已删除低位件", "DEL-SS", cableZoneId, 0, 100, 1);
        // 未分配分区低位件：必须进台账
        insertAccessory("未分配低位件", "NA-SS", null, 5, 10, 0);
    }

    private Long insertAccessory(String name, String model, Long zoneId,
                                 int stock, Integer safetyStock, int deleted) {
        Accessory accessory = new Accessory();
        accessory.setAccessoryName(name);
        accessory.setModel(model);
        accessory.setZoneTagId(zoneId);
        accessory.setStockQuantity(stock);
        accessory.setSafetyStock(safetyStock);
        accessory.setDeleted(deleted);
        accessoryMapper.insert(accessory);
        return accessory.getId();
    }

    @Test
    void ledgerMatchesArchiveFilterAndGap() {
        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, false);

        // 仅 2 条：分区内六类网线 + 未分配低位件
        assertEquals(2, rows.size());
        Map<String, SafetyStockVO> byName = rows.stream()
                .collect(Collectors.toMap(SafetyStockVO::getAccessoryName, Function.identity()));

        SafetyStockVO cat6 = byName.get("六类网线");
        assertEquals(600, cat6.getStockQuantity());
        assertEquals(800, cat6.getSafetyStock());
        assertEquals(200, cat6.getGapQuantity());
        assertEquals("线缆布线区", cat6.getZoneTagName());
        assertFalse(cat6.getUnassignedZone());

        SafetyStockVO unassigned = byName.get("未分配低位件");
        assertTrue(unassigned.getUnassignedZone());
        assertNull(unassigned.getZoneTagId());
        assertNull(unassigned.getZoneTagName());
        assertEquals(5, unassigned.getStockQuantity());
        assertEquals(10, unassigned.getSafetyStock());
        assertEquals(5, unassigned.getGapQuantity());

        // 未设下限、充足、已删除的都不进
        assertTrue(byName.keySet().stream().noneMatch(name ->
                name.contains("RVV") || name.contains("超五类") || name.contains("已删除")));
    }

    @Test
    void ledgerFilteredByZoneReturnsOnlyThatZone() {
        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(cableZoneId, false);

        // 线缆布线区只有六类网线一个低位件；未分配低位件不混入
        assertEquals(1, rows.size());
        assertEquals("六类网线", rows.get(0).getAccessoryName());
        assertEquals(cableZoneId, rows.get(0).getZoneTagId());
        assertFalse(rows.get(0).getUnassignedZone());
    }

    @Test
    void ledgerFilteredUnassignedOnlyReturnsUnassignedRows() {
        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, true);

        // 未分配分区可单独筛出，分区内的六类网线不混入
        assertEquals(1, rows.size());
        assertEquals("未分配低位件", rows.get(0).getAccessoryName());
        assertTrue(rows.get(0).getUnassignedZone());
    }

    @Test
    void ledgerZoneFilterWithNoLowItemsReturnsEmpty() {
        ZoneTag emptyZone = new ZoneTag();
        emptyZone.setTagName("监控设备区");
        emptyZone.setTagCode("ZONE-MONITOR-SS");
        emptyZone.setSortOrder(3);
        zoneTagMapper.insert(emptyZone);

        // 该分区内没有任何低位配件
        assertTrue(accessoryService.listSafetyStockShortages(emptyZone.getId(), false).isEmpty());
    }

    @Test
    void refreshAfterStockOrLimitChangeRecomputes() {
        assertEquals(2, accessoryService.listSafetyStockShortages(null, false).size());

        // 把六类网线补到下限：不再进台账
        Accessory cat6 = accessoryMapper.selectOne(new com.baomidou.mybatisplus.core.conditions
                .query.LambdaQueryWrapper<Accessory>().eq(Accessory::getModel, "CAT6-SS"));
        cat6.setStockQuantity(800);
        accessoryMapper.updateById(cat6);

        List<SafetyStockVO> afterRestock = accessoryService.listSafetyStockShortages(null, false);
        assertEquals(1, afterRestock.size());
        assertEquals("未分配低位件", afterRestock.get(0).getAccessoryName());

        // 给未分配低位件清掉下限：台账为空
        Accessory unassigned = accessoryMapper.selectOne(new com.baomidou.mybatisplus.core.conditions
                .query.LambdaQueryWrapper<Accessory>().eq(Accessory::getModel, "NA-SS"));
        unassigned.setSafetyStock(null);
        accessoryMapper.updateById(unassigned);

        assertTrue(accessoryService.listSafetyStockShortages(null, false).isEmpty());
    }

    @Test
    void clearingLimitThroughServiceUpdatePersistsNullAndRemovesFromLedger() {
        // 走与前端“编辑-清空（不设下限）-保存”一致的 service.update 路径，
        // 验证 null 下限确实落库（updateById 默认会忽略 null，需要显式同步）
        assertEquals(2, accessoryService.listSafetyStockShortages(null, false).size());

        Accessory cat6 = accessoryMapper.selectOne(new com.baomidou.mybatisplus.core.conditions
                .query.LambdaQueryWrapper<Accessory>().eq(Accessory::getModel, "CAT6-SS"));

        AccessoryDTO dto = new AccessoryDTO();
        dto.setId(cat6.getId());
        dto.setAccessoryName(cat6.getAccessoryName());
        dto.setModel(cat6.getModel());
        dto.setZoneTagId(cableZoneId);
        dto.setStockQuantity(600);
        dto.setSafetyStock(null);
        accessoryService.update(dto);

        assertNull(accessoryMapper.selectById(cat6.getId()).getSafetyStock());
        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(null, false);
        assertEquals(1, rows.size());
        assertEquals("未分配低位件", rows.get(0).getAccessoryName());
    }
}
