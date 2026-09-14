package com.factory.security.service.impl;

import com.factory.security.dto.InspectionCreateDTO;
import com.factory.security.dto.InspectionQualifyDTO;
import com.factory.security.dto.InspectionResultDTO;
import com.factory.security.entity.Accessory;
import com.factory.security.entity.InspectionOrder;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.InspectionOrderMapper;
import com.factory.security.service.InspectionOrderService;
import com.factory.security.vo.InspectionOrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 辅材送检单生命周期集成测试（H2 MySQL 兼容模式，真实 SQL 落库）：
 * 按已建档配件新建（批次/实验室必填）、新建即待回样、未回样不能标合格、
 * 写回结论后变已回样才可标合格、结论不可重复写回、按是否已回样筛选、
 * 已删除配件不能送检
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:inspection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:schema-it.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
})
class InspectionOrderLifecycleTest {

    @Autowired
    private InspectionOrderService inspectionOrderService;

    @Autowired
    private InspectionOrderMapper inspectionOrderMapper;

    @Autowired
    private AccessoryMapper accessoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long cameraId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM inspection_order");
        jdbcTemplate.update("DELETE FROM accessory");

        Accessory camera = new Accessory();
        camera.setAccessoryName("防爆摄像头");
        camera.setModel("DS-2CD3T46-INSP");
        camera.setSpecUnit("MP");
        camera.setStockQuantity(12);
        camera.setDeleted(0);
        accessoryMapper.insert(camera);
        cameraId = camera.getId();
    }

    private InspectionCreateDTO createDTO(String batch, String lab) {
        InspectionCreateDTO dto = new InspectionCreateDTO();
        dto.setAccessoryId(cameraId);
        dto.setBatchNo(batch);
        dto.setLabName(lab);
        return dto;
    }

    @Test
    void createRequiresBatchAndLab() {
        InspectionCreateDTO missingBatch = createDTO("  ", "厂区中心实验室");
        assertThrows(RuntimeException.class, () -> inspectionOrderService.create(missingBatch));

        InspectionCreateDTO missingLab = createDTO("B2026-09", "   ");
        assertThrows(RuntimeException.class, () -> inspectionOrderService.create(missingLab));
    }

    @Test
    void createRejectsDeletedOrUnknownAccessory() {
        InspectionCreateDTO dto = createDTO("B2026-09", "厂区中心实验室");
        dto.setAccessoryId(cameraId + 99999);
        assertThrows(RuntimeException.class, () -> inspectionOrderService.create(dto));
    }

    @Test
    void newOrderIsPendingSampleAndSnapshotsAccessory() {
        Long id = inspectionOrderService.create(createDTO("  B2026-09 ", "  厂区中心实验室 "));

        InspectionOrder order = inspectionOrderMapper.selectById(id);
        assertNotNull(order.getInspectionNo());
        assertTrue(order.getInspectionNo().startsWith("SJ"));
        assertEquals(cameraId, order.getAccessoryId());
        assertEquals("防爆摄像头", order.getAccessoryName());
        // 批次与实验室提交前后端自动 trim
        assertEquals("B2026-09", order.getBatchNo());
        assertEquals("厂区中心实验室", order.getLabName());
        assertEquals(0, order.getSampleReturned());
        assertEquals(0, order.getQualified());
        assertNull(order.getLabConclusion());
    }

    @Test
    void cannotQualifyBeforeSampleReturned() {
        Long id = inspectionOrderService.create(createDTO("B2026-09", "厂区中心实验室"));

        InspectionQualifyDTO qualify = new InspectionQualifyDTO();
        qualify.setQualified(1);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> inspectionOrderService.qualify(id, qualify));
        assertTrue(ex.getMessage().contains("尚未回样"));

        // 被拒绝后单据仍是待回样、未判定
        InspectionOrder order = inspectionOrderMapper.selectById(id);
        assertEquals(0, order.getSampleReturned());
        assertEquals(0, order.getQualified());
    }

    @Test
    void writeResultThenQualifyLifecycle() {
        Long id = inspectionOrderService.create(createDTO("B2026-09", "厂区中心实验室"));

        // 纯空白结论不能回样
        InspectionResultDTO blank = new InspectionResultDTO();
        blank.setLabConclusion("   ");
        assertThrows(RuntimeException.class, () -> inspectionOrderService.writeResult(id, blank));

        InspectionResultDTO result = new InspectionResultDTO();
        result.setLabConclusion("外观与电气性能检测合格");
        inspectionOrderService.writeResult(id, result);

        InspectionOrder returned = inspectionOrderMapper.selectById(id);
        assertEquals(1, returned.getSampleReturned());
        assertEquals("外观与电气性能检测合格", returned.getLabConclusion());
        assertNotNull(returned.getSampleReturnTime());

        // 结论只能写回一次
        assertThrows(RuntimeException.class, () -> inspectionOrderService.writeResult(id, result));

        // 已回样才能标合格
        InspectionQualifyDTO qualify = new InspectionQualifyDTO();
        qualify.setQualified(1);
        inspectionOrderService.qualify(id, qualify);
        InspectionOrder qualified = inspectionOrderMapper.selectById(id);
        assertEquals(1, qualified.getQualified());
        assertNotNull(qualified.getQualifiedTime());

        // 取消合格标记
        InspectionQualifyDTO unqualify = new InspectionQualifyDTO();
        unqualify.setQualified(0);
        inspectionOrderService.qualify(id, unqualify);
        assertEquals(0, inspectionOrderMapper.selectById(id).getQualified());
        assertNull(inspectionOrderMapper.selectById(id).getQualifiedTime());
    }

    @Test
    void pageFiltersBySampleReturned() {
        Long pendingId = inspectionOrderService.create(createDTO("B2026-09", "厂区中心实验室"));
        Long returnedId = inspectionOrderService.create(createDTO("B2026-10", "第三方检测机构"));
        InspectionResultDTO result = new InspectionResultDTO();
        result.setLabConclusion("合格");
        inspectionOrderService.writeResult(returnedId, result);

        Page<InspectionOrderVO> pendingPage = inspectionOrderService.page(1, 10, 0, null, null);
        assertEquals(1, pendingPage.getTotal());
        assertEquals(pendingId, pendingPage.getRecords().get(0).getId());
        assertEquals("待回样", pendingPage.getRecords().get(0).getSampleReturnedText());

        Page<InspectionOrderVO> returnedPage = inspectionOrderService.page(1, 10, 1, null, null);
        assertEquals(1, returnedPage.getTotal());
        InspectionOrderVO vo = returnedPage.getRecords().get(0);
        assertEquals(returnedId, vo.getId());
        assertEquals("已回样", vo.getSampleReturnedText());
        assertEquals("合格", vo.getLabConclusion());
        // 仅回样未判定合格：合格文案为“未判定合格”
        assertEquals("未判定合格", vo.getQualifiedText());
    }

    @Test
    void keywordMatchesOrderNoOrAccessoryName() {
        Long id = inspectionOrderService.create(createDTO("B2026-09", "厂区中心实验室"));
        InspectionOrder saved = inspectionOrderMapper.selectById(id);

        Page<InspectionOrderVO> byName = inspectionOrderService.page(1, 10, null, null, "防爆");
        assertEquals(1, byName.getTotal());

        Page<InspectionOrderVO> byNo = inspectionOrderService.page(1, 10, null, null, saved.getInspectionNo());
        assertEquals(1, byNo.getTotal());

        Page<InspectionOrderVO> none = inspectionOrderService.page(1, 10, null, null, "不存在的配件");
        assertEquals(0, none.getTotal());
    }
}
