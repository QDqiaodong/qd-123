package com.factory.security.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.FiberSpliceCommissionDTO;
import com.factory.security.dto.FiberSpliceJointCreateDTO;
import com.factory.security.dto.FiberSpliceJointUpdateDTO;
import com.factory.security.dto.FiberSpliceVoidDTO;
import com.factory.security.entity.FiberSpliceJoint;
import com.factory.security.entity.ZoneTag;
import com.factory.security.mapper.FiberSpliceJointMapper;
import com.factory.security.mapper.ZoneTagMapper;
import com.factory.security.vo.FiberSpliceJointVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiberSpliceJointServiceImplTest {

    // 名称与 ServiceImpl 继承的 baseMapper 字段一致，保证 @InjectMocks 按名注入
    @Mock
    private FiberSpliceJointMapper baseMapper;

    @Mock
    private ZoneTagMapper zoneTagMapper;

    @InjectMocks
    private FiberSpliceJointServiceImpl service;

    @BeforeEach
    void injectBaseMapper() throws Exception {
        java.lang.reflect.Field baseMapperField =
                com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                        .getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(service, baseMapper);
    }

    private ZoneTag zone(Long id, String name) {
        ZoneTag zone = new ZoneTag();
        zone.setId(id);
        zone.setTagName(name);
        return zone;
    }

    private FiberSpliceJoint joint(Long id, int otdr, int commissionable, int status) {
        FiberSpliceJoint joint = new FiberSpliceJoint();
        joint.setId(id);
        joint.setSpliceNo("RJ-" + id);
        joint.setZoneTagId(3L);
        joint.setZoneName("接头终端区");
        joint.setReserveMeters(12);
        joint.setOtdrPassed(otdr);
        joint.setCommissionable(commissionable);
        joint.setStatus(status);
        return joint;
    }

    @Test
    void createRejectsDuplicateSpliceNo() {
        FiberSpliceJointCreateDTO dto = new FiberSpliceJointCreateDTO();
        dto.setSpliceNo("RJ-001");
        dto.setZoneTagId(3L);
        dto.setReserveMeters(12);
        dto.setOtdrPassed(0);
        when(baseMapper.selectCount(any())).thenReturn(1L);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(dto));
        assertTrue(ex.getMessage().contains("已登记"));
    }

    @Test
    void createRejectsMissingZone() {
        FiberSpliceJointCreateDTO dto = new FiberSpliceJointCreateDTO();
        dto.setSpliceNo("RJ-002");
        dto.setZoneTagId(99L);
        dto.setReserveMeters(12);
        dto.setOtdrPassed(0);
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(zoneTagMapper.selectById(99L)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.create(dto));
        assertTrue(ex.getMessage().contains("所属分区不存在"));
    }

    @Test
    void commissionRejectedWhenOtdrNotPassed() {
        when(baseMapper.selectById(2L)).thenReturn(joint(2L, 0, 0, 0));
        FiberSpliceCommissionDTO dto = new FiberSpliceCommissionDTO();
        dto.setCommissionable(1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.commission(2L, dto));
        assertTrue(ex.getMessage().contains("尚未通过 OTDR"));
    }

    @Test
    void commissionAllowedWhenOtdrPassed() {
        when(baseMapper.selectById(1L)).thenReturn(joint(1L, 1, 0, 0));
        when(baseMapper.updateById(any(FiberSpliceJoint.class))).thenReturn(1);
        FiberSpliceCommissionDTO dto = new FiberSpliceCommissionDTO();
        dto.setCommissionable(1);

        service.commission(1L, dto);
        verify(baseMapper).updateById(any(FiberSpliceJoint.class));
    }

    @Test
    void commissionRejectedForVoidedJoint() {
        when(baseMapper.selectById(9L)).thenReturn(joint(9L, 1, 0, 1));
        FiberSpliceCommissionDTO dto = new FiberSpliceCommissionDTO();
        dto.setCommissionable(1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.commission(9L, dto));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    void uncommissionAllowedWhenOtdrNotPassed() {
        // 取消可投运（0）不要求已过 OTDR
        when(baseMapper.selectById(3L)).thenReturn(joint(3L, 0, 1, 0));
        when(baseMapper.updateById(any(FiberSpliceJoint.class))).thenReturn(1);
        FiberSpliceCommissionDTO dto = new FiberSpliceCommissionDTO();
        dto.setCommissionable(0);

        service.commission(3L, dto);
        verify(baseMapper).updateById(any(FiberSpliceJoint.class));
    }

    @Test
    void updateRejectsChangingOtdrOffWhileCommissionable() {
        when(baseMapper.selectById(1L)).thenReturn(joint(1L, 1, 1, 0));
        FiberSpliceJointUpdateDTO dto = new FiberSpliceJointUpdateDTO();
        dto.setZoneTagId(3L);
        dto.setReserveMeters(12);
        dto.setOtdrPassed(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.update(1L, dto));
        assertTrue(ex.getMessage().contains("先取消可投运标记"));
    }

    @Test
    void updateRejectsVoidedJoint() {
        when(baseMapper.selectById(9L)).thenReturn(joint(9L, 1, 0, 1));
        FiberSpliceJointUpdateDTO dto = new FiberSpliceJointUpdateDTO();
        dto.setZoneTagId(3L);
        dto.setReserveMeters(12);
        dto.setOtdrPassed(1);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.update(9L, dto));
        assertTrue(ex.getMessage().contains("已作废"));
    }

    @Test
    void updateSuccessResnapshotsZoneName() {
        when(baseMapper.selectById(1L)).thenReturn(joint(1L, 0, 0, 0));
        when(zoneTagMapper.selectById(2L)).thenReturn(zone(2L, "线缆布线区"));
        when(baseMapper.updateById(any(FiberSpliceJoint.class))).thenReturn(1);
        FiberSpliceJointUpdateDTO dto = new FiberSpliceJointUpdateDTO();
        dto.setZoneTagId(2L);
        dto.setReserveMeters(30);
        dto.setOtdrPassed(1);

        service.update(1L, dto);
        verify(baseMapper).updateById(any(FiberSpliceJoint.class));
    }

    @Test
    void voidOnlyMarksStatusAndRejectsRepeat() {
        when(baseMapper.selectById(1L)).thenReturn(joint(1L, 1, 1, 0));
        when(baseMapper.updateById(any(FiberSpliceJoint.class))).thenReturn(1);
        FiberSpliceVoidDTO dto = new FiberSpliceVoidDTO();
        dto.setReason("熔接异常");

        service.voidJoint(1L, dto);
        verify(baseMapper).updateById(any(FiberSpliceJoint.class));

        when(baseMapper.selectById(9L)).thenReturn(joint(9L, 0, 0, 1));
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.voidJoint(9L, new FiberSpliceVoidDTO()));
        assertTrue(ex.getMessage().contains("不能重复作废"));
    }

    @Test
    void pageBuildsVoWithZoneDeletedFlag() {
        FiberSpliceJoint j = joint(1L, 1, 1, 0);
        Page<FiberSpliceJoint> mapperPage = new Page<>(1, 10, 1);
        mapperPage.setRecords(List.of(j));
        when(baseMapper.selectPage(any(), any())).thenReturn(mapperPage);
        // 分区标签查不到：zoneDeleted 应为 true，但快照分区名仍展示
        when(zoneTagMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        Page<FiberSpliceJointVO> result = service.page(1, 10, null, 3L, null, null, null);
        FiberSpliceJointVO vo = result.getRecords().get(0);
        assertEquals("RJ-1", vo.getSpliceNo());
        assertEquals("接头终端区", vo.getZoneName());
        assertTrue(vo.getZoneDeleted());
        assertEquals("可投运", vo.getCommissionableText());
        assertFalse(vo.getStatusText().equals("已作废"));
    }
}
