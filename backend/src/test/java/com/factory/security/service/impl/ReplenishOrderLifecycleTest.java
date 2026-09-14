package com.factory.security.service.impl;

import com.factory.security.dto.ReplenishCancelDTO;
import com.factory.security.dto.ReplenishCreateDTO;
import com.factory.security.dto.ReplenishItemDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.ReplenishOrder;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.ReplenishOrderItemMapper;
import com.factory.security.mapper.ReplenishOrderMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.ReplenishOrderService;
import com.factory.security.vo.ReplishOrderItemVO;
import com.factory.security.vo.ReplenishOrderDetailVO;
import com.factory.security.vo.ReplenishZoneSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补货单生命周期集成测试（H2 MySQL 兼容模式，真实 SQL 落库）：
 * 台账勾选低位配件生成草稿、按分区汇总缺口件数、草稿改数量、
 * 提交回写档案补货单号/待补数量并锁定、作废清除待补标记、草稿删除；
 * 同一配件不能同时挂在两张有效补货单（行级条件更新兜底并发）。
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:replenish;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:schema-it.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class ReplenishOrderLifecycleTest {

    @Autowired
    private ReplenishOrderService replenishOrderService;

    @Autowired
    private ReplenishOrderMapper replenishOrderMapper;

    @Autowired
    private ReplenishOrderItemMapper replenishOrderItemMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cableZoneId;
    private Long bridgeZoneId;
    private Long cat6Id;
    private Long crystalId;
    private Long unassignedId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM replenish_order_item");
        jdbcTemplate.update("DELETE FROM replenish_order");
        jdbcTemplate.update("DELETE FROM accessory");
        jdbcTemplate.update("DELETE FROM zone_tag");

        ZoneTag bridge = new ZoneTag();
        bridge.setTagName("弱电桥架区");
        bridge.setTagCode("ZONE-BRIDGE-RP");
        bridge.setSortOrder(1);
        zoneTagMapper.insert(bridge);
        bridgeZoneId = bridge.getId();

        ZoneTag cable = new ZoneTag();
        cable.setTagName("线缆布线区");
        cable.setTagCode("ZONE-CABLE-RP");
        cable.setSortOrder(2);
        zoneTagMapper.insert(cable);
        cableZoneId = cable.getId();

        // 六类网线：现存 600，下限 800，缺口 200（线缆区）
        cat6Id = insertAccessory("六类网线", "CAT6-RP", cableZoneId, 600, 800);
        // 水晶头：现存 80，下限 100，缺口 20（桥架区，用于验证多分区汇总）
        crystalId = insertAccessory("水晶头", "RJ45-RP", bridgeZoneId, 80, 100);
        // 未分配分区低位件：现存 5，下限 10，缺口 5
        unassignedId = insertAccessory("未分配低位件", "NA-RP", null, 5, 10);
        // 充足件与未设下限件：不应被接受进单
        insertAccessory("超五类网线", "CAT5e-RP", cableZoneId, 1000, 800);
        insertAccessory("RVV电源线", "RVV-RP", cableZoneId, 1, null);
    }

    private Long insertAccessory(String name, String model, Long zoneId, int stock, Integer safetyStock) {
        Accessory accessory = new Accessory();
        accessory.setAccessoryName(name);
        accessory.setModel(model);
        accessory.setZoneTagId(zoneId);
        accessory.setStockQuantity(stock);
        accessory.setSafetyStock(safetyStock);
        accessoryMapper.insert(accessory);
        return accessory.getId();
    }

    private ReplenishCreateDTO createDTO(ReplenishCreateDTO.ReplenishItemCreateDTO... items) {
        ReplenishCreateDTO dto = new ReplenishCreateDTO();
        dto.setItems(List.of(items));
        return dto;
    }

    private ReplenishCreateDTO.ReplenishItemCreateDTO item(Long accessoryId) {
        return item(accessoryId, null);
    }

    private ReplenishCreateDTO.ReplenishItemCreateDTO item(Long accessoryId, Integer quantity) {
        ReplenishCreateDTO.ReplenishItemCreateDTO dto = new ReplenishCreateDTO.ReplenishItemCreateDTO();
        dto.setAccessoryId(accessoryId);
        dto.setReplenishQuantity(quantity);
        return dto;
    }

    private ReplenishItemDTO quantities(ReplenishItemDTO.ReplenishQuantityDTO... items) {
        ReplenishItemDTO dto = new ReplenishItemDTO();
        dto.setItems(List.of(items));
        return dto;
    }

    private ReplenishItemDTO.ReplenishQuantityDTO quantity(Long itemId, int quantity) {
        ReplenishItemDTO.ReplenishQuantityDTO dto = new ReplenishItemDTO.ReplenishQuantityDTO();
        dto.setItemId(itemId);
        dto.setReplenishQuantity(quantity);
        return dto;
    }

    private ReplenishOrderDetailVO detail(Long id) {
        return replenishOrderService.getDetailById(id);
    }

    private Long itemIdOf(Long orderId, Long accessoryId) {
        return detail(orderId).getItems().stream()
                .filter(row -> row.getAccessoryId().equals(accessoryId))
                .findFirst().orElseThrow().getId();
    }

    private Long accessoryIdByModel(String model) {
        return accessoryMapper.selectList(null).stream()
                .filter(a -> model.equals(a.getModel()))
                .map(Accessory::getId)
                .findFirst().orElseThrow();
    }

    @Test
    void createSnapshotsGapsAndSummarizesByZoneWithUnassignedLast() {
        Long id = replenishOrderService.create(
                createDTO(item(cat6Id), item(crystalId), item(unassignedId)));

        ReplenishOrderDetailVO detail = detail(id);
        assertEquals(0, detail.getHeader().getStatus());
        assertEquals(3, detail.getHeader().getItemCount());
        // 200 + 20 + 5 = 225
        assertEquals(225, detail.getHeader().getTotalQuantity());
        assertEquals(3, detail.getHeader().getZoneCount());

        // 明细默认补货数量等于实时缺口
        Map<Long, Integer> replenishByAccessory = detail.getItems().stream()
                .collect(Collectors.toMap(
                        ReplenishOrderItemVO::getAccessoryId,
                        ReplenishOrderItemVO::getReplenishQuantity));
        assertEquals(200, replenishByAccessory.get(cat6Id));
        assertEquals(20, replenishByAccessory.get(crystalId));
        assertEquals(5, replenishByAccessory.get(unassignedId));

        // 按分区汇总：桥架(排序号1) → 线缆(排序号2) → 未分配殿后
        List<ReplenishZoneSummaryVO> zones = detail.getZoneSummaries();
        assertEquals(List.of("弱电桥架区", "线缆布线区", "未分配分区"),
                zones.stream().map(ReplenishZoneSummaryVO::getZoneName).collect(Collectors.toList()));
        assertEquals(1, zones.get(0).getAccessoryCount());
        assertEquals(20, zones.get(0).getTotalQuantity());
        assertEquals(200, zones.get(1).getTotalQuantity());
        assertEquals(5, zones.get(2).getTotalQuantity());
        assertTrue(zones.get(2).getUnassignedZone());

        // 草稿阶段不占用配件待补标记
        Accessory cat6 = accessoryMapper.selectById(cat6Id);
        assertNull(cat6.getReplenishOrderId());
        assertNull(cat6.getReplenishPendingQuantity());
    }

    @Test
    void createRejectsNonLowUnsetOverGapOrDuplicatePending() {
        // 现存不低于下限
        RuntimeException ex1 = assertThrows(RuntimeException.class, () ->
                replenishOrderService.create(createDTO(item(accessoryIdByModel("CAT5e-RP")))));
        assertTrue(ex1.getMessage().contains("不低于"));

        // 未设下限
        RuntimeException ex2 = assertThrows(RuntimeException.class, () ->
                replenishOrderService.create(createDTO(item(accessoryIdByModel("RVV-RP")))));
        assertTrue(ex2.getMessage().contains("未设置安全库存下限"));

        // 补货数量超过缺口
        RuntimeException ex3 = assertThrows(RuntimeException.class, () ->
                replenishOrderService.create(createDTO(item(cat6Id, 201))));
        assertTrue(ex3.getMessage().contains("不能超过缺口"));

        // 提交一张单占用六类网线后，再次生成含该配件的单据直接拒绝
        Long firstId = replenishOrderService.create(createDTO(item(cat6Id)));
        replenishOrderService.submit(firstId);
        RuntimeException ex4 = assertThrows(RuntimeException.class, () ->
                replenishOrderService.create(createDTO(item(cat6Id))));
        assertTrue(ex4.getMessage().contains("已在补货单"));
    }

    @Test
    void draftCanUpdateQuantityWithinGapAndHeaderTotalRecomputes() {
        Long id = replenishOrderService.create(createDTO(item(cat6Id), item(crystalId)));
        Long cat6ItemId = itemIdOf(id, cat6Id);

        replenishOrderService.updateItems(id, quantities(quantity(cat6ItemId, 150)));

        ReplenishOrderDetailVO detail = detail(id);
        // 150 + 20 = 170
        assertEquals(170, detail.getHeader().getTotalQuantity());
        ReplenishZoneSummaryVO cableZone = detail.getZoneSummaries().stream()
                .filter(z -> "线缆布线区".equals(z.getZoneName())).findFirst().orElseThrow();
        assertEquals(150, cableZone.getTotalQuantity());

        // 超过缺口拒绝，头表合计不被破坏
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                replenishOrderService.updateItems(id, quantities(quantity(cat6ItemId, 201))));
        assertTrue(ex.getMessage().contains("不能超过缺口"));
        assertEquals(170, detail(id).getHeader().getTotalQuantity());
    }

    @Test
    void submitWritesBackOrderNoAndPendingQuantityThenLocksQuantity() {
        Long id = replenishOrderService.create(createDTO(item(cat6Id), item(unassignedId)));
        replenishOrderService.submit(id);

        ReplenishOrder order = replenishOrderMapper.selectById(id);
        assertEquals(1, order.getStatus());
        String orderNo = order.getReplenishNo();

        // 档案可见补货单号与待补数量
        Accessory cat6 = accessoryMapper.selectById(cat6Id);
        assertEquals(id, cat6.getReplenishOrderId());
        assertEquals(orderNo, cat6.getReplenishOrderNo());
        assertEquals(200, cat6.getReplenishPendingQuantity());
        Accessory unassigned = accessoryMapper.selectById(unassignedId);
        assertEquals(5, unassigned.getReplenishPendingQuantity());

        // 已提交单不能再改数量
        Long cat6ItemId = itemIdOf(id, cat6Id);
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                replenishOrderService.updateItems(id, quantities(quantity(cat6ItemId, 100))));
        assertTrue(ex.getMessage().contains("已提交"));

        // 重复提交拒绝、已提交单不能直接删除
        assertThrows(RuntimeException.class, () -> replenishOrderService.submit(id));
        assertThrows(RuntimeException.class, () -> replenishOrderService.delete(id));
    }

    @Test
    void cancelSubmittedOrderClearsPendingMarksAndKeepsReadOnlyOrder() {
        Long id = replenishOrderService.create(createDTO(item(cat6Id), item(unassignedId)));
        replenishOrderService.submit(id);
        assertEquals(id, accessoryMapper.selectById(cat6Id).getReplenishOrderId());

        ReplenishCancelDTO cancelDTO = new ReplenishCancelDTO();
        cancelDTO.setCancelReason("采购计划调整");
        replenishOrderService.cancel(id, cancelDTO);

        // 档案待补标记全部清除
        Accessory cat6 = accessoryMapper.selectById(cat6Id);
        assertNull(cat6.getReplenishOrderId());
        assertNull(cat6.getReplenishOrderNo());
        assertNull(cat6.getReplenishPendingQuantity());
        assertNull(accessoryMapper.selectById(unassignedId).getReplenishOrderId());

        // 单据置为已作废、只读留档
        ReplenishOrder order = replenishOrderMapper.selectById(id);
        assertEquals(2, order.getStatus());
        assertEquals("采购计划调整", order.getCancelReason());
        assertThrows(RuntimeException.class, () ->
                replenishOrderService.updateItems(id, quantities(quantity(itemIdOf(id, cat6Id), 1))));
        assertThrows(RuntimeException.class, () -> replenishOrderService.delete(id));
        // 作废后配件可重新生成补货单（待补标记已让出）
        Long secondId = replenishOrderService.create(createDTO(item(cat6Id)));
        assertEquals(0, replenishOrderMapper.selectById(secondId).getStatus());
    }

    @Test
    void clearPendingByOrderIdDoesNotTouchNewerOrderMark() {
        // clear 带 orderId 条件：标记已改挂到新单B（999）时，作废旧单A不应误清新单标记
        Long orderA = replenishOrderService.create(createDTO(item(cat6Id)));
        replenishOrderService.submit(orderA);

        jdbcTemplate.update(
                "UPDATE accessory SET replenish_order_id = 999, replenish_order_no = 'BH-NEW', "
                        + "replenish_pending_quantity = 200 WHERE id = ?", cat6Id);
        accessoryMapper.clearReplenishPending(orderA);

        Accessory cat6 = accessoryMapper.selectById(cat6Id);
        assertEquals(999L, cat6.getReplenishOrderId());
        assertEquals("BH-NEW", cat6.getReplenishOrderNo());
    }

    @Test
    void submitRejectsWhenAccessoryStolenByAnotherOrderAndRollsBackWholeOrder() {
        // 两张草稿都含水晶头：A 提交占用后，B 提交必须整单失败（行级条件更新返回 0 行）
        Long orderA = replenishOrderService.create(createDTO(item(cat6Id), item(crystalId)));
        Long orderB = replenishOrderService.create(createDTO(item(crystalId), item(unassignedId)));

        replenishOrderService.submit(orderA);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> replenishOrderService.submit(orderB));
        assertTrue(ex.getMessage().contains("占用"));

        // B 整单回滚：仍为草稿，未分配件没有被半途回写待补标记
        assertEquals(0, replenishOrderMapper.selectById(orderB).getStatus());
        assertNull(accessoryMapper.selectById(unassignedId).getReplenishOrderId());
        // A 正常占用两个配件
        assertEquals(orderA, accessoryMapper.selectById(crystalId).getReplenishOrderId());
        assertEquals(orderA, accessoryMapper.selectById(cat6Id).getReplenishOrderId());
    }

    @Test
    void submitRejectsDeletedAccessory() {
        Long id = replenishOrderService.create(createDTO(item(cat6Id)));
        // 草稿期间配件被软删除：提交拒绝，单据仍为草稿
        jdbcTemplate.update("UPDATE accessory SET deleted = 1 WHERE id = ?", cat6Id);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> replenishOrderService.submit(id));
        assertTrue(ex.getMessage().contains("已删除"));
        assertEquals(0, replenishOrderMapper.selectById(id).getStatus());
    }

    @Test
    void draftDeleteRemovesOrderAndItemsWithoutTouchingArchive() {
        Long id = replenishOrderService.create(createDTO(item(cat6Id)));
        replenishOrderService.delete(id);

        assertNull(replenishOrderMapper.selectById(id));
        assertEquals(0, replenishOrderItemMapper.selectList(null).size());
        // 草稿本来就没占用配件档案，保持干净
        assertNull(accessoryMapper.selectById(cat6Id).getReplenishOrderId());
    }

    @Test
    void accessoryCarriesPendingFieldsAfterSubmitAsLedgerFlagSource() {
        // 台账行 replenishPending 由配件档案待补字段装配：提交后字段齐全、单号以 BH 开头
        Long id = replenishOrderService.create(createDTO(item(cat6Id)));
        replenishOrderService.submit(id);
        Accessory pending = accessoryMapper.selectById(cat6Id);
        assertFalse(pending.getReplenishOrderId() == null);
        assertTrue(pending.getReplenishOrderNo().startsWith("BH"));
    }
}
