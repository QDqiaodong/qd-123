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
import org.springframework.jdbc.core.JdbcTemplate;

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
 * 同分区内按缺口从大到小排序，缺口达到下限一半及以上标为紧急；
 * 换分区或改下限刷新后，顺序与紧急标记随新缺口一致（未分配分区同样生效）。
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cableZoneId;
    private Long bridgeZoneId;

    @BeforeEach
    void seed() {
        // H2 内存库随 Spring 上下文复用，各测试方法共用同一库；
        // 每个用例前先清空本类涉及的两张表，保证条数/顺序断言不被其他用例的种子数据污染
        jdbcTemplate.update("DELETE FROM accessory");
        jdbcTemplate.update("DELETE FROM zone_tag");
        // 桥架分区排序号更小：用于验证全集顺序“先按分区排序号，再按区内缺口降序”
        ZoneTag bridgeZone = new ZoneTag();
        bridgeZone.setTagName("弱电桥架区");
        bridgeZone.setTagCode("ZONE-BRIDGE-SS");
        bridgeZone.setSortOrder(1);
        zoneTagMapper.insert(bridgeZone);
        bridgeZoneId = bridgeZone.getId();

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
        // 缺口 200 不足下限 800 的一半（400）：普通行，非紧急
        assertFalse(cat6.getUrgent());

        SafetyStockVO unassigned = byName.get("未分配低位件");
        assertTrue(unassigned.getUnassignedZone());
        assertNull(unassigned.getZoneTagId());
        assertNull(unassigned.getZoneTagName());
        assertEquals(5, unassigned.getStockQuantity());
        assertEquals(10, unassigned.getSafetyStock());
        assertEquals(5, unassigned.getGapQuantity());
        // 缺口 5 恰好为下限 10 的一半（2*5>=10，边界含在内）：未分配分区同样标紧急
        assertTrue(unassigned.getUrgent());

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
        // updateById 默认忽略 null 字段，清空下限需显式 SQL（与 service.update 内 updateSafetyStock 同理）
        jdbcTemplate.update("UPDATE accessory SET safety_stock = NULL WHERE id = ?", unassigned.getId());

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

    /** 紧急排序专用配件：用 ORD-SS- 型号前缀与种子数据隔离，各测试方法独立插入不影响断言 */
    private Long insertSortAccessory(String name, String modelSuffix, Long zoneId,
                                     int stock, int safetyStock) {
        return insertAccessory(name, "ORD-SS-" + modelSuffix, zoneId, stock, safetyStock, 0);
    }

    private List<SafetyStockVO> sortRows() {
        return accessoryService.listSafetyStockShortages(null, false).stream()
                .filter(row -> row.getModel() != null && row.getModel().startsWith("ORD-SS-"))
                .collect(Collectors.toList());
    }

    private Accessory findSortAccessory(String modelSuffix) {
        return accessoryMapper.selectOne(new com.baomidou.mybatisplus.core.conditions
                .query.LambdaQueryWrapper<Accessory>()
                .eq(Accessory::getModel, "ORD-SS-" + modelSuffix));
    }

    @Test
    void rowsSortedByGapDescWithinZoneAndUnassignedLastWithUrgentFlag() {
        // 排序号更小的桥架分区：缺口 90（紧急）、缺口 50=下限一半（边界紧急）
        insertSortAccessory("排序-桥架小缺口", "BRIDGE-SMALL", bridgeZoneId, 50, 100);
        insertSortAccessory("排序-桥架大缺口", "BRIDGE-BIG", bridgeZoneId, 10, 100);
        // 线缆分区（排序号 2）：缺口 40 < 下限 100 的一半，普通行
        insertSortAccessory("排序-线缆普通件", "CABLE-NORMAL", cableZoneId, 60, 100);
        // 未分配分区：缺口 9（紧急），未分配整体殿后
        insertSortAccessory("排序-未分配急件", "NA-URGENT", null, 1, 10);

        List<SafetyStockVO> rows = sortRows();

        // 顺序：桥架分区（sortOrder=1）两条按缺口降序 → 线缆分区 → 未分配殿后
        assertEquals(List.of("排序-桥架大缺口", "排序-桥架小缺口", "排序-线缆普通件", "排序-未分配急件"),
                rows.stream().map(SafetyStockVO::getAccessoryName).collect(Collectors.toList()));

        Map<String, SafetyStockVO> bySuffix = rows.stream().collect(Collectors.toMap(
                row -> row.getModel().substring("ORD-SS-".length()), Function.identity()));
        // 缺口 90 达下限 100 的 90%：紧急
        assertTrue(bySuffix.get("BRIDGE-BIG").getUrgent());
        // 缺口 50 恰好为下限一半：边界含在内，紧急
        assertTrue(bySuffix.get("BRIDGE-SMALL").getUrgent());
        // 缺口 40 不足下限一半：普通
        assertFalse(bySuffix.get("CABLE-NORMAL").getUrgent());
        // 未分配分区的急件同样按口径标紧急
        assertTrue(bySuffix.get("NA-URGENT").getUrgent());
        assertTrue(bySuffix.get("NA-URGENT").getUnassignedZone());
    }

    @Test
    void changingLimitRefreshesOrderAndUrgentFlagToNewGap() {
        // 同分区两个低位件：A 缺口 100（紧急），B 缺口 5（普通），A 在前
        insertSortAccessory("排序-改限A件", "LIMIT-A", cableZoneId, 0, 100);
        insertSortAccessory("排序-改限B件", "LIMIT-B", cableZoneId, 95, 100);

        List<SafetyStockVO> before = sortRows();
        assertEquals(List.of("排序-改限A件", "排序-改限B件"),
                before.stream().map(SafetyStockVO::getAccessoryName).collect(Collectors.toList()));
        assertTrue(before.get(0).getUrgent());
        assertFalse(before.get(1).getUrgent());

        // A 改下限 30、现存 16：缺口 14，2*14=28 < 30，由紧急转普通
        Accessory a = findSortAccessory("LIMIT-A");
        a.setSafetyStock(30);
        a.setStockQuantity(16);
        accessoryMapper.updateById(a);
        // B 现存调到 80：缺口 20，仍普通，但缺口反超 A
        Accessory b = findSortAccessory("LIMIT-B");
        b.setStockQuantity(80);
        accessoryMapper.updateById(b);

        List<SafetyStockVO> after = sortRows();
        // 顺序随新缺口翻转：B(20) 在 A(14) 前
        assertEquals(List.of("排序-改限B件", "排序-改限A件"),
                after.stream().map(SafetyStockVO::getAccessoryName).collect(Collectors.toList()));
        Map<String, SafetyStockVO> bySuffix = after.stream().collect(Collectors.toMap(
                row -> row.getModel().substring("ORD-SS-".length()), Function.identity()));
        assertEquals(20, bySuffix.get("LIMIT-B").getGapQuantity());
        assertFalse(bySuffix.get("LIMIT-B").getUrgent());
        assertEquals(14, bySuffix.get("LIMIT-A").getGapQuantity());
        // 紧急标记随改下限后的新缺口翻转
        assertFalse(bySuffix.get("LIMIT-A").getUrgent());
    }

    @Test
    void movingAccessoryAcrossZonesRefreshesGroupAndUrgentFlag() {
        // 桥架分区一个缺口 90 的紧急件
        Long id = insertSortAccessory("排序-换分区件", "ZONE-MOVE", bridgeZoneId, 10, 100);

        List<SafetyStockVO> before = sortRows();
        assertEquals(1, before.size());
        assertEquals(bridgeZoneId, before.get(0).getZoneTagId());
        assertTrue(before.get(0).getUrgent());

        // 移到未分配分区并把下限下调：刷新后归到未分配组（全集殿后）、标记按新缺口重算。
        // updateById 默认忽略 null 字段，置空分区必须显式 SQL（与 service.updateZone 落库路径一致）
        jdbcTemplate.update(
                "UPDATE accessory SET zone_tag_id = NULL, safety_stock = 10, stock_quantity = 8 WHERE id = ?",
                id);

        List<SafetyStockVO> after = sortRows();
        assertEquals(1, after.size());
        SafetyStockVO row = after.get(0);
        assertTrue(row.getUnassignedZone());
        assertNull(row.getZoneTagId());
        // 缺口 2 < 下限 10 的一半：不再紧急
        assertFalse(row.getUrgent());
        assertEquals(2, row.getGapQuantity());

        // 只筛未分配分区时同样能取到，排序/标记规则一致
        List<SafetyStockVO> unassignedOnly = accessoryService.listSafetyStockShortages(null, true)
                .stream()
                .filter(r -> "ORD-SS-ZONE-MOVE".equals(r.getModel()))
                .collect(Collectors.toList());
        assertEquals(1, unassignedOnly.size());
        assertFalse(unassignedOnly.get(0).getUrgent());
    }
}
