package com.factory.security.service.impl;

import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.CableReel;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.CableReelMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.service.CableReelService;
import com.factory.security.vo.CableReelVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整盘电源线建档→开盘→扣米链路集成测试（H2 MySQL 兼容模式，真实 SQL 落库）：
 * 建档为未开盘且不动档案米数；重复盘号、对未开盘盘扣米均被拒绝；
 * 开盘把整盘米数一次性计入档案；扣米在同事务内同步扣减盘上剩余与档案现存量，
 * 刷新（重新查询）后二者一致；同一配件同时只允许一个已开盘（uk_open_accessory 兜底）。
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:cablereel;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:schema-it.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class CableReelFlowIT {

    @Autowired
    private CableReelService cableReelService;

    @Autowired
    private CableReelMapper cableReelMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private ZoneTagMapper zoneTagMapper;

    private Long accessoryId;

    @BeforeEach
    void seed() {
        ZoneTag zone = new ZoneTag();
        zone.setTagName("线缆布线区");
        zone.setTagCode("ZONE-CABLE-REEL-IT");
        zone.setSortOrder(2);
        zoneTagMapper.insert(zone);

        Accessory accessory = new Accessory();
        accessory.setAccessoryName("RVV电源线");
        accessory.setModel("RVV-REEL-IT");
        accessory.setSpecUnit("m");
        accessory.setZoneTagId(zone.getId());
        // 档案初始为 0：整盘是该配件米数的唯一来源，开盘后“盘上剩余 == 档案米数”恒成立
        accessory.setStockQuantity(0);
        accessoryMapper.insert(accessory);
        accessoryId = accessory.getId();
    }

    private CableReelCreateDTO createDTO(String reelNo, int meters) {
        CableReelCreateDTO dto = new CableReelCreateDTO();
        dto.setReelNo(reelNo);
        dto.setAccessoryId(accessoryId);
        dto.setRemainingMeters(meters);
        return dto;
    }

    private CableReelDeductDTO deductDTO(int meters) {
        CableReelDeductDTO dto = new CableReelDeductDTO();
        dto.setMeters(meters);
        return dto;
    }

    private CableReelVO findVO(Long id) {
        Page<CableReelVO> page = cableReelService.page(1, 100, null, null, accessoryId);
        return page.getRecords().stream().filter(vo -> vo.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void unopenedReelDoesNotChangeArchiveAndCannotBeDeducted() {
        Long id = cableReelService.create(createDTO("P-IT-001", 200));

        // 建档后为未开盘，盘上 200 米，但配件档案仍是 0 米（米数尚未入账）
        CableReel saved = cableReelMapper.selectById(id);
        assertEquals(0, saved.getStatus());
        assertEquals(200, saved.getRemainingMeters());
        assertEquals(0, accessoryMapper.selectById(accessoryId).getStockQuantity());
        // 未开盘不参与一致性判定
        assertNull(findVO(id).getStockMatched());

        // 没开过的盘不能拿去扣米
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(id, deductDTO(30)));
        assertTrue(ex.getMessage().contains("尚未开盘"));
        assertEquals(200, cableReelMapper.selectById(id).getRemainingMeters());
        assertEquals(0, accessoryMapper.selectById(accessoryId).getStockQuantity());
    }

    @Test
    void duplicateReelNoRejected() {
        cableReelService.create(createDTO("P-IT-DUP", 200));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.create(createDTO("P-IT-DUP", 100)));
        assertTrue(ex.getMessage().contains("同一盘号不能建两次"));
    }

    @Test
    void openAddsMetersToArchiveAndDeductKeepsBothInSync() {
        Long id = cableReelService.create(createDTO("P-IT-002", 200));
        cableReelService.open(id);

        // 开盘后整盘 200 米一次性计入档案：档案此前为 0，入账 200，盘上仍剩 200，二者一致
        assertEquals(1, cableReelMapper.selectById(id).getStatus());
        assertEquals(200, cableReelMapper.selectById(id).getRemainingMeters());
        assertEquals(200, accessoryMapper.selectById(accessoryId).getStockQuantity());
        assertTrue(findVO(id).getStockMatched());

        // 扣 30 米：盘上剩 170，档案 170，同事务联动
        cableReelService.deduct(id, deductDTO(30));
        assertEquals(170, cableReelMapper.selectById(id).getRemainingMeters());
        assertEquals(170, accessoryMapper.selectById(accessoryId).getStockQuantity());
        assertTrue(findVO(id).getStockMatched());

        // 再扣超过剩余的米数被拒绝，两边都不变
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cableReelService.deduct(id, deductDTO(1000)));
        assertTrue(ex.getMessage().contains("不足扣减"));
        assertEquals(170, cableReelMapper.selectById(id).getRemainingMeters());
        assertEquals(170, accessoryMapper.selectById(accessoryId).getStockQuantity());
    }

    @Test
    void secondReelSameAccessoryCanBeCreatedButCannotOpenConcurrently() {
        Long first = cableReelService.create(createDTO("P-IT-A", 200));
        Long second = cableReelService.create(createDTO("P-IT-B", 100));
        cableReelService.open(first);

        // 同一配件已有一个已开盘盘：第二个盘开盘被拒（uk_open_accessory 兜底），米数不入账
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(second));
        assertTrue(ex.getMessage().contains("同一配件同时只能开一个盘"));
        assertEquals(0, cableReelMapper.selectById(second).getStatus());
        // 第一个盘 200 已入账，第二个盘 100 未入账
        assertEquals(200, accessoryMapper.selectById(accessoryId).getStockQuantity());
    }

    @Test
    void openRejectedWhenAccessoryAlreadyCarriesOtherStock() {
        // 另建一个档案已有 500 米库存的配件：开盘会让“盘上剩余 == 档案米数”恒不成立，必须拒绝
        Accessory stocked = new Accessory();
        stocked.setAccessoryName("RVVP电源线");
        stocked.setModel("RVVP-REEL-IT");
        stocked.setSpecUnit("m");
        stocked.setStockQuantity(500);
        accessoryMapper.insert(stocked);

        CableReelCreateDTO dto = new CableReelCreateDTO();
        dto.setReelNo("P-IT-STOCK");
        dto.setAccessoryId(stocked.getId());
        dto.setRemainingMeters(200);
        Long id = cableReelService.create(dto);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.open(id));
        assertTrue(ex.getMessage().contains("档案现存"));
        assertEquals(0, cableReelMapper.selectById(id).getStatus());
        // 米数绝不能入账，档案仍是 500
        assertEquals(500, accessoryMapper.selectById(stocked.getId()).getStockQuantity());
    }

    @Test
    void openedReelCannotBeDeletedButUnopenedCan() {
        Long unopened = cableReelService.create(createDTO("P-IT-DEL-1", 50));
        Long opened = cableReelService.create(createDTO("P-IT-DEL-2", 50));
        cableReelService.open(opened);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cableReelService.delete(opened));
        assertTrue(ex.getMessage().contains("已开盘"));
        // 已开盘盘仍在，且状态不变
        assertEquals(1, cableReelMapper.selectById(opened).getStatus());

        cableReelService.delete(unopened);
        assertNull(cableReelMapper.selectById(unopened));
    }
}
