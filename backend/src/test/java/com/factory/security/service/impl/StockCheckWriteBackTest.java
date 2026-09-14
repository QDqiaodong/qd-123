package com.factory.security.service.impl;

import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.StockCheck;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.StockCheckMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.StockCheckService;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 盘点确认回写链路集成测试（H2 MySQL 兼容模式，真实 SQL 落库）：
 * 待确认登记不改档案现存量；确认成功后档案立即为新现存量，重新查询结果一致
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:stockcheck;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:schema-it.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class StockCheckWriteBackTest {

    @Autowired
    private StockCheckService stockCheckService;

    @Autowired
    private StockCheckMapper stockCheckMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    private Long zoneId;
    private Long cat6Id;
    private Long cat5eId;

    @BeforeEach
    void seedZoneAndAccessories() {
        ZoneTag zone = new ZoneTag();
        zone.setTagName("线缆布线区");
        zone.setTagCode("ZONE-CABLE-IT");
        zone.setSortOrder(2);
        zoneTagMapper.insert(zone);
        zoneId = zone.getId();

        cat5eId = insertAccessory("超五类网线", "CAT5e-UTP-IT", 1000);
        cat6Id = insertAccessory("六类网线", "CAT6-UTP-IT", 600);
    }

    private Long insertAccessory(String name, String model, int stock) {
        Accessory accessory = new Accessory();
        accessory.setAccessoryName(name);
        accessory.setModel(model);
        accessory.setZoneTagId(zoneId);
        accessory.setStockQuantity(stock);
        accessoryMapper.insert(accessory);
        return accessory.getId();
    }

    private Long openCheck() {
        StockCheckCreateDTO createDTO = new StockCheckCreateDTO();
        createDTO.setZoneTagId(zoneId);
        return stockCheckService.create(createDTO);
    }

    private void recordAll(Long checkId, int cat5eActual, int cat6Actual) {
        StockCheckDetailVO detail = stockCheckService.getDetailById(checkId);
        List<StockCheckItemDTO.StockCheckActualDTO> actuals = detail.getItems().stream()
                .filter(item -> !Boolean.TRUE.equals(item.getAccessoryDeleted()))
                .map(item -> {
                    StockCheckItemDTO.StockCheckActualDTO actual = new StockCheckItemDTO.StockCheckActualDTO();
                    actual.setItemId(item.getId());
                    actual.setActualQuantity(item.getAccessoryId().equals(cat6Id) ? cat6Actual : cat5eActual);
                    return actual;
                })
                .collect(Collectors.toList());
        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(actuals);
        stockCheckService.recordItems(checkId, dto);
    }

    @Test
    void pendingRecordDoesNotTouchArchiveAndConfirmWritesBackImmediately() {
        Long checkId = openCheck();

        // 待确认登记：只登记实盘数，档案现存量必须保持开盘前的数
        recordAll(checkId, 980, 650);
        assertEquals(1000, accessoryMapper.selectById(cat5eId).getStockQuantity());
        assertEquals(600, accessoryMapper.selectById(cat6Id).getStockQuantity());

        // 确认成功：档案现存量立即按实盘数回写；差异说明随单据持久化，刷新后仍在
        stockCheckService.confirm(checkId, confirmDTO("  月度盘点：差异已现场复核  "));
        assertEquals(980, accessoryMapper.selectById(cat5eId).getStockQuantity());
        assertEquals(650, accessoryMapper.selectById(cat6Id).getStockQuantity());

        // 重新进入档案页（重新查询）数字仍与回写后一致
        assertEquals(650, accessoryMapper.selectById(cat6Id).getStockQuantity());
        StockCheck confirmed = stockCheckMapper.selectById(checkId);
        assertEquals(1, confirmed.getStatus());
        // 说明按 trim 后落库，重新查询仍是同一份
        assertEquals("月度盘点：差异已现场复核", confirmed.getConfirmRemark());
    }

    @Test
    void confirmWithoutRemarkRejectsAndKeepsArchiveUntouched() {
        Long checkId = openCheck();
        recordAll(checkId, 980, 650);

        // 无说明（空 DTO）不能确认，库存保持账面数
        assertThrows(RuntimeException.class,
                () -> stockCheckService.confirm(checkId, new StockCheckConfirmDTO()));
        assertEquals(1000, accessoryMapper.selectById(cat5eId).getStockQuantity());
        assertEquals(600, accessoryMapper.selectById(cat6Id).getStockQuantity());

        // 纯空白说明同样拒绝；补填说明后可正常确认回写
        StockCheckConfirmDTO blank = new StockCheckConfirmDTO();
        blank.setConfirmRemark("   ");
        assertThrows(RuntimeException.class, () -> stockCheckService.confirm(checkId, blank));
        stockCheckService.confirm(checkId, confirmDTO("账实差异已查明并处理"));
        assertEquals(980, accessoryMapper.selectById(cat5eId).getStockQuantity());
        assertEquals("账实差异已查明并处理", stockCheckMapper.selectById(checkId).getConfirmRemark());
    }

    private StockCheckConfirmDTO confirmDTO(String remark) {
        StockCheckConfirmDTO dto = new StockCheckConfirmDTO();
        dto.setConfirmRemark(remark);
        return dto;
    }

    @Test
    void confirmWithUnrecordedItemRejectsAndKeepsArchiveUntouched() {
        Long checkId = openCheck();
        // 只登记六类网线，超五类网线未登记
        StockCheckDetailVO detail = stockCheckService.getDetailById(checkId);
        Long cat6ItemId = detail.getItems().stream()
                .filter(item -> item.getAccessoryId().equals(cat6Id))
                .findFirst().orElseThrow().getId();
        StockCheckItemDTO.StockCheckActualDTO actual = new StockCheckItemDTO.StockCheckActualDTO();
        actual.setItemId(cat6ItemId);
        actual.setActualQuantity(650);
        StockCheckItemDTO dto = new StockCheckItemDTO();
        dto.setItems(List.of(actual));
        stockCheckService.recordItems(checkId, dto);

        // 确认必须被拒绝，且已登记项的库存也不能被部分回写（说明已填，卡在未登记校验）
        assertThrows(RuntimeException.class,
                () -> stockCheckService.confirm(checkId, confirmDTO("差异说明已填写")));
        assertEquals(600, accessoryMapper.selectById(cat6Id).getStockQuantity());
        assertEquals(1000, accessoryMapper.selectById(cat5eId).getStockQuantity());
    }

    @Test
    void pageFiltersConfirmedChecksByHasRemark() {
        // 直接构造四张单：已确认有说明、已确认纯空白说明（视为无说明）、已确认无说明、待确认
        insertCheck("PD-FILTER-1", 1, "月度盘点正常差异");
        insertCheck("PD-FILTER-2", 1, "   ");
        insertCheck("PD-FILTER-3", 1, null);
        insertCheck("PD-FILTER-4", 0, null);

        Page<StockCheckVO> withRemark = stockCheckService.page(1, 10, null, null, false, true);
        List<String> withRemarkNos = withRemark.getRecords().stream()
                .map(StockCheckVO::getCheckNo).collect(Collectors.toList());
        assertEquals(1, withRemark.getTotal());
        assertTrue(withRemarkNos.contains("PD-FILTER-1"));

        // 无说明：纯空白与 NULL 都算，且只看已确认单（待确认单 PD-FILTER-4 不混入）
        Page<StockCheckVO> withoutRemark = stockCheckService.page(1, 10, null, null, false, false);
        List<String> withoutRemarkNos = withoutRemark.getRecords().stream()
                .map(StockCheckVO::getCheckNo).collect(Collectors.toList());
        assertEquals(2, withoutRemark.getTotal());
        assertTrue(withoutRemarkNos.contains("PD-FILTER-2"));
        assertTrue(withoutRemarkNos.contains("PD-FILTER-3"));
        assertFalse(withoutRemarkNos.contains("PD-FILTER-4"));
        // 返回行携带说明内容，刷新后仍可展示
        assertEquals("月度盘点正常差异", withRemark.getRecords().get(0).getConfirmRemark());
    }

    /** 直接插入盘点单头：status=1 已确认 / 0 待确认，confirmRemark 模拟历史数据可能为 NULL 或纯空白 */
    private void insertCheck(String checkNo, int status, String confirmRemark) {
        StockCheck check = new StockCheck();
        check.setCheckNo(checkNo);
        check.setZoneTagId(zoneId);
        check.setZoneName("线缆布线区");
        check.setUnassignedZone(0);
        check.setStatus(status);
        check.setItemCount(0);
        check.setDiffCount(0);
        check.setConfirmRemark(confirmRemark);
        stockCheckMapper.insert(check);
    }
}
