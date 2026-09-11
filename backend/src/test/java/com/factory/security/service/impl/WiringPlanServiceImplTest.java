package com.factory.security.service.impl;

import com.factory.security.entity.Accessory;
import com.factory.security.entity.WiringPlan;
import com.factory.security.entity.WiringPlanDetail;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.WiringPlanDetailMapper;
import com.factory.security.mapper.WiringPlanMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.vo.WiringPlanDetailVO;
import com.factory.security.vo.WiringPlanExportRowVO;
import com.factory.security.vo.WiringPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WiringPlanServiceImplTest {

    // 名称与 ServiceImpl 继承的 baseMapper 字段一致，保证 @InjectMocks 按名注入
    @Mock
    private WiringPlanMapper baseMapper;

    @Mock
    private WiringPlanDetailMapper wiringPlanDetailMapper;

    @Mock
    private AccessoryMapper accessoryMapper;

    @Mock
    private ZoneTagMapper zoneTagMapper;

    @InjectMocks
    private WiringPlanServiceImpl wiringPlanService;

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // ServiceImpl#baseMapper 经类型擦除为 BaseMapper，4 个 Mapper mock 类型均可匹配，
        // Mockito 泛型候选过滤在该组合下不会注入该字段，这里按字段名显式注入
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(wiringPlanService, baseMapper);
    }

    private WiringPlan existingPlan(Long id, Integer status) {
        WiringPlan plan = new WiringPlan();
        plan.setId(id);
        plan.setPlanName("测试方案");
        plan.setStatus(status);
        return plan;
    }

    @Test
    void enableExistingPlanSucceeds() {
        when(baseMapper.selectById(1L)).thenReturn(existingPlan(1L, 0));
        when(baseMapper.updateById(any(WiringPlan.class))).thenReturn(1);

        boolean result = wiringPlanService.updateStatus(1L, 1);

        assertTrue(result);
        ArgumentCaptor<WiringPlan> captor = ArgumentCaptor.forClass(WiringPlan.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    void disableExistingPlanSucceeds() {
        when(baseMapper.selectById(1L)).thenReturn(existingPlan(1L, 1));
        when(baseMapper.updateById(any(WiringPlan.class))).thenReturn(1);

        boolean result = wiringPlanService.updateStatus(1L, 0);

        assertTrue(result);
        ArgumentCaptor<WiringPlan> captor = ArgumentCaptor.forClass(WiringPlan.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void nonexistentPlanIdThrowsClearError() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> wiringPlanService.updateStatus(999L, 1));

        assertEquals("布线方案不存在或已被删除", e.getMessage());
        verify(baseMapper, never()).updateById(any(WiringPlan.class));
    }

    @Test
    void deletedPlanIdThrowsClearError() {
        // 方案已被删除，数据库查不到记录
        when(baseMapper.selectById(5L)).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> wiringPlanService.updateStatus(5L, 0));

        assertEquals("布线方案不存在或已被删除", e.getMessage());
        verify(baseMapper, never()).updateById(any(WiringPlan.class));
    }

    @Test
    void illegalStatusValueRejected() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> wiringPlanService.updateStatus(1L, 2));

        assertEquals("启用状态不合法", e.getMessage());
        verify(baseMapper, never()).selectById(any());
        verify(baseMapper, never()).updateById(any(WiringPlan.class));
    }

    @Test
    void nullStatusRejected() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> wiringPlanService.updateStatus(1L, null));

        assertEquals("启用状态不合法", e.getMessage());
        verify(baseMapper, never()).updateById(any(WiringPlan.class));
    }

    @Test
    void updateAffectingZeroRowsThrowsError() {
        // 查询时方案存在，更新时已被并发删除（影响行数为 0）
        when(baseMapper.selectById(1L)).thenReturn(existingPlan(1L, 1));
        when(baseMapper.updateById(any(WiringPlan.class))).thenReturn(0);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> wiringPlanService.updateStatus(1L, 0));

        assertEquals("布线方案状态更新失败，请刷新后重试", e.getMessage());
    }

    @Test
    void rapidConsecutiveTogglesApplyInOrder() {
        // 连续快速切换：启用 -> 停用 -> 启用，每次都应按顺序生效
        when(baseMapper.selectById(1L)).thenReturn(existingPlan(1L, 0));
        when(baseMapper.updateById(any(WiringPlan.class))).thenReturn(1);

        wiringPlanService.updateStatus(1L, 1);
        wiringPlanService.updateStatus(1L, 0);
        wiringPlanService.updateStatus(1L, 1);

        ArgumentCaptor<WiringPlan> captor = ArgumentCaptor.forClass(WiringPlan.class);
        verify(baseMapper, times(3)).updateById(captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getStatus());
        assertEquals(0, captor.getAllValues().get(1).getStatus());
        assertEquals(1, captor.getAllValues().get(2).getStatus());
    }

    // -------------------- 导出当前筛选结果 --------------------

    private WiringPlan buildPlan(Long id, String name, String scene, Integer status, String createTime) {
        WiringPlan plan = new WiringPlan();
        plan.setId(id);
        plan.setPlanName(name);
        plan.setScene(scene);
        plan.setStatus(status);
        plan.setCreateTime(LocalDateTime.parse(createTime));
        return plan;
    }

    private WiringPlanDetail buildDetail(Long id, Long planId, Long accessoryId, Integer quantity) {
        WiringPlanDetail detail = new WiringPlanDetail();
        detail.setId(id);
        detail.setPlanId(planId);
        detail.setAccessoryId(accessoryId);
        detail.setQuantity(quantity);
        return detail;
    }

    private Accessory buildAccessory(Long id, String name, String specUnit, Long zoneTagId) {
        Accessory accessory = new Accessory();
        accessory.setId(id);
        accessory.setAccessoryName(name);
        accessory.setSpecUnit(specUnit);
        accessory.setZoneTagId(zoneTagId);
        return accessory;
    }

    private ZoneTag buildZone(Long id, String name, Integer sortOrder) {
        ZoneTag zoneTag = new ZoneTag();
        zoneTag.setId(id);
        zoneTag.setTagName(name);
        zoneTag.setSortOrder(sortOrder);
        return zoneTag;
    }

    @Test
    void emptyFilterResultReturnsEmptyRowsWithoutQueryingDetails() {
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows("不存在的关键词", 1);

        assertTrue(rows.isEmpty());
        verify(wiringPlanDetailMapper, never()).selectList(any());
        verify(accessoryMapper, never()).selectBatchIds(any());
    }

    @Test
    void exportRowsKeepPlanBoundaryAndSortDetailsByZone() {
        // 方案1（启用，创建晚）含 3 个配件：分区2、分区1、未分配分区；方案2（停用）含 1 个配件
        WiringPlan plan1 = buildPlan(1L, "厂区外围监控布线方案", "厂区外围监控", 1, "2026-09-02T10:00:00");
        WiringPlan plan2 = buildPlan(2L, "机房网络布线方案", "机房布线", 0, "2026-09-01T10:00:00");
        when(baseMapper.selectList(any())).thenReturn(Arrays.asList(plan1, plan2));

        List<WiringPlanDetail> allDetails = Arrays.asList(
                buildDetail(101L, 1L, 11L, 12),
                buildDetail(102L, 1L, 5L, 600),
                buildDetail(103L, 1L, 9L, 300),
                buildDetail(201L, 2L, 4L, 800)
        );
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(allDetails);

        when(accessoryMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildAccessory(11L, "防爆摄像头", "MP", 5L),
                buildAccessory(5L, "RVV电源线", "mm²", 2L),
                buildAccessory(9L, "扎带", "mm", null),
                buildAccessory(4L, "六类网线", "mm", 2L)
        ));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildZone(2L, "线缆布线区", 2),
                buildZone(5L, "监控设备区", 5)
        ));

        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows(null, null);

        // 方案1 三行 + 方案2 一行，方案按创建时间倒序、方案边界（planId 连续重复）保留
        assertEquals(4, rows.size());
        assertEquals(Arrays.asList(1L, 1L, 1L, 2L),
                rows.stream().map(WiringPlanExportRowVO::getPlanId).collect(java.util.stream.Collectors.toList()));

        // 方案1 内按分区排序号升序：线缆布线区(2) -> 监控设备区(5) -> 未分配分区
        assertEquals("RVV电源线", rows.get(0).getAccessoryName());
        assertEquals("线缆布线区", rows.get(0).getZoneTagName());
        assertEquals("600", rows.get(0).getQuantityText());
        assertEquals("mm²", rows.get(0).getSpecUnit());
        assertEquals("厂区外围监控布线方案", rows.get(0).getPlanName());
        assertEquals("厂区外围监控", rows.get(0).getScene());
        assertEquals("启用", rows.get(0).getStatusText());

        assertEquals("防爆摄像头", rows.get(1).getAccessoryName());
        assertEquals("监控设备区", rows.get(1).getZoneTagName());

        assertEquals("扎带", rows.get(2).getAccessoryName());
        assertEquals("未分配分区", rows.get(2).getZoneTagName());
        // 未分配分区不影响规格单位的正常输出
        assertEquals("mm", rows.get(2).getSpecUnit());

        // 方案2 独立成行，停用状态展示正确
        WiringPlanExportRowVO plan2Row = rows.get(3);
        assertEquals("机房网络布线方案", plan2Row.getPlanName());
        assertEquals("机房布线", plan2Row.getScene());
        assertEquals("停用", plan2Row.getStatusText());
        assertEquals("六类网线", plan2Row.getAccessoryName());
        assertEquals("线缆布线区", plan2Row.getZoneTagName());
        assertEquals("800", plan2Row.getQuantityText());
        assertEquals("mm", plan2Row.getSpecUnit());
    }

    @Test
    void planWithoutDetailsProducesPlaceholderRow() {
        // 没有任何配件的方案仍需在导出中保留一条仅含方案信息的行
        WiringPlan plan = buildPlan(3L, "暂无配件的方案", null, 1, "2026-09-03T10:00:00");
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(plan));
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows(null, null);

        assertEquals(1, rows.size());
        WiringPlanExportRowVO row = rows.get(0);
        assertEquals(3L, row.getPlanId());
        assertEquals("暂无配件的方案", row.getPlanName());
        assertEquals("启用", row.getStatusText());
        assertEquals("", row.getScene());
        assertEquals("", row.getAccessoryName());
        assertEquals("", row.getZoneTagName());
        assertEquals("", row.getQuantityText());
        assertEquals("", row.getSpecUnit());
        verify(accessoryMapper, never()).selectBatchIds(any());
    }

    @Test
    void veryLongPlanNameIsExportedUnchanged() {
        // 超长方案名称（含中文与特殊字符）不能截断或导致组装失败
        String longName = "超长方案名称".repeat(50) + ",含逗号\"与引号\"";
        WiringPlan plan = buildPlan(4L, longName, "超长场景", 1, "2026-09-04T10:00:00");
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(plan));
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(
                Collections.singletonList(buildDetail(301L, 4L, 7L, 1)));
        when(accessoryMapper.selectBatchIds(any())).thenReturn(
                Collections.singletonList(buildAccessory(7L, "水晶头", "mm", 3L)));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(
                Collections.singletonList(buildZone(3L, "接头终端区", 3)));

        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows("超长", 1);

        assertEquals(1, rows.size());
        assertEquals(longName, rows.get(0).getPlanName());
        assertEquals("水晶头", rows.get(0).getAccessoryName());
        assertEquals("接头终端区", rows.get(0).getZoneTagName());
    }

    @Test
    void deletedAccessoryFallsBackToClearText() {
        // 配件被删除（批量装配查不到）时行仍保留，并给出明确兜底文案
        WiringPlan plan = buildPlan(5L, "含失效配件的方案", "监控", 0, "2026-09-05T10:00:00");
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(plan));
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(
                Collections.singletonList(buildDetail(401L, 5L, 999L, 3)));
        when(accessoryMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows(null, 0);

        assertEquals(1, rows.size());
        assertEquals("配件已删除", rows.get(0).getAccessoryName());
        assertEquals("未分配分区", rows.get(0).getZoneTagName());
        assertEquals("", rows.get(0).getSpecUnit());
        assertEquals("3", rows.get(0).getQuantityText());
        assertEquals("停用", rows.get(0).getStatusText());
    }

    // -------------------- 详情页分区排序 --------------------

    /**
     * 数据库以乱序返回明细：分区排序号升序；排序号相同的分区按分区名区分先后；
     * 同分区内按配件名称排序；未分配分区与已删除配件排最后
     */
    @Test
    void detailSortedByZoneSortOrderThenAccessoryName() {
        WiringPlan plan = buildPlan(1L, "厂区外围监控布线方案", "厂区外围监控", 1, "2026-09-02T10:00:00");
        when(baseMapper.selectById(1L)).thenReturn(plan);

        // 模拟编辑后重插/刷新导致的乱序返回
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(Arrays.asList(
                buildDetail(13L, 1L, 999L, 3),
                buildDetail(11L, 1L, 101L, 12),
                buildDetail(10L, 1L, 100L, 600),
                buildDetail(15L, 1L, 104L, 40),
                buildDetail(12L, 1L, 102L, 300),
                buildDetail(14L, 1L, 103L, 800)
        ));
        // 配件 999 已被删除，批量查询查不到
        when(accessoryMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildAccessory(100L, "RVV电源线", "mm²", 2L),
                buildAccessory(103L, "六类网线", "mm", 2L),
                buildAccessory(101L, "防爆摄像头", "MP", 5L),
                buildAccessory(104L, "PVC线管", "mm", 6L),
                buildAccessory(102L, "扎带", "mm", null)
        ));
        // 监控设备区与弱电配管区排序号重复（均为 5）
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildZone(2L, "线缆布线区", 2),
                buildZone(5L, "监控设备区", 5),
                buildZone(6L, "弱电配管区", 5)
        ));

        WiringPlanVO vo = wiringPlanService.getDetailById(1L);

        assertEquals(6, vo.getDetailCount());
        assertEquals(6, vo.getDetails().size());
        assertEquals(
                Arrays.asList("RVV电源线", "六类网线", "PVC线管", "防爆摄像头", "配件已删除", "扎带"),
                vo.getDetails().stream().map(WiringPlanDetailVO::getAccessoryName)
                        .collect(java.util.stream.Collectors.toList()));
        assertEquals(
                Arrays.asList("线缆布线区", "线缆布线区", "弱电配管区", "监控设备区", null, null),
                vo.getDetails().stream().map(WiringPlanDetailVO::getZoneTagName)
                        .collect(java.util.stream.Collectors.toList()));

        // 已删除配件：保留明细行并给出兜底文案，分区为空由前端归入“未分配分区”
        WiringPlanDetailVO deletedRow = vo.getDetails().get(4);
        assertEquals(999L, deletedRow.getAccessoryId());
        assertNull(deletedRow.getZoneTagId());
        assertNull(deletedRow.getZoneTagName());
    }

    @Test
    void detailWithNoDetailsReturnsEmptyList() {
        // 空分区场景：方案没有任何配件明细
        WiringPlan plan = buildPlan(2L, "暂无配件的方案", null, 1, "2026-09-03T10:00:00");
        when(baseMapper.selectById(2L)).thenReturn(plan);
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(Collections.emptyList());

        WiringPlanVO vo = wiringPlanService.getDetailById(2L);

        assertEquals(0, vo.getDetailCount());
        assertTrue(vo.getDetails().isEmpty());
        verify(accessoryMapper, never()).selectBatchIds(any());
        verify(zoneTagMapper, never()).selectBatchIds(any());
    }

    @Test
    void detailOrderIsConsistentWithExportOrder() {
        // 同一份数据，详情接口与导出接口的配件顺序必须一致
        WiringPlan plan = buildPlan(1L, "厂区外围监控布线方案", "厂区外围监控", 1, "2026-09-02T10:00:00");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.selectList(any())).thenReturn(Collections.singletonList(plan));
        when(wiringPlanDetailMapper.selectList(any())).thenReturn(Arrays.asList(
                buildDetail(13L, 1L, 999L, 3),
                buildDetail(11L, 1L, 101L, 12),
                buildDetail(10L, 1L, 100L, 600),
                buildDetail(12L, 1L, 102L, 300),
                buildDetail(14L, 1L, 103L, 800)
        ));
        when(accessoryMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildAccessory(100L, "RVV电源线", "mm²", 2L),
                buildAccessory(103L, "六类网线", "mm", 2L),
                buildAccessory(101L, "防爆摄像头", "MP", 5L),
                buildAccessory(102L, "扎带", "mm", null)
        ));
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                buildZone(2L, "线缆布线区", 2),
                buildZone(5L, "监控设备区", 5)
        ));

        List<String> exportOrder = wiringPlanService.listExportRows(null, null).stream()
                .map(WiringPlanExportRowVO::getAccessoryName)
                .collect(java.util.stream.Collectors.toList());
        List<String> detailOrder = wiringPlanService.getDetailById(1L).getDetails().stream()
                .map(WiringPlanDetailVO::getAccessoryName)
                .collect(java.util.stream.Collectors.toList());

        assertEquals(exportOrder, detailOrder);
        assertEquals(Arrays.asList("RVV电源线", "六类网线", "防爆摄像头", "配件已删除", "扎带"), detailOrder);
    }
}
