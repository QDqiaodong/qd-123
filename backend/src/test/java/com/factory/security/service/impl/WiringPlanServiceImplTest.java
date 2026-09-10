package com.factory.security.service.impl;

import com.factory.security.entity.WiringPlan;
import com.factory.security.mapper.AccessoryMapper;
import com.factory.security.mapper.WiringPlanDetailMapper;
import com.factory.security.mapper.WiringPlanMapper;
import com.factory.security.mapper.ZoneTagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
