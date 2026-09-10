package com.factory.security.controller;

import com.factory.security.service.WiringPlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WiringPlanController.class)
class WiringPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WiringPlanService wiringPlanService;

    @Test
    void enablePlanReturnsSuccess() throws Exception {
        when(wiringPlanService.updateStatus(1L, 1)).thenReturn(true);

        mockMvc.perform(put("/wiring-plan/1/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wiringPlanService).updateStatus(1L, 1);
    }

    @Test
    void disablePlanReturnsSuccess() throws Exception {
        when(wiringPlanService.updateStatus(1L, 0)).thenReturn(true);

        mockMvc.perform(put("/wiring-plan/1/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wiringPlanService).updateStatus(1L, 0);
    }

    @Test
    void nonexistentPlanIdReturnsClearError() throws Exception {
        when(wiringPlanService.updateStatus(999L, 1))
                .thenThrow(new RuntimeException("布线方案不存在或已被删除"));

        mockMvc.perform(put("/wiring-plan/999/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("布线方案不存在或已被删除"));
    }

    @Test
    void illegalStatusValueReturnsClearError() throws Exception {
        when(wiringPlanService.updateStatus(1L, 3))
                .thenThrow(new RuntimeException("启用状态不合法"));

        mockMvc.perform(put("/wiring-plan/1/status").param("status", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("启用状态不合法"));
    }

    @Test
    void rapidConsecutiveTogglesAllSucceed() throws Exception {
        // 连续快速切换：启用 -> 停用 -> 启用，每次请求都应正常处理
        when(wiringPlanService.updateStatus(eq(1L), anyInt())).thenReturn(true);

        mockMvc.perform(put("/wiring-plan/1/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/wiring-plan/1/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/wiring-plan/1/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wiringPlanService, times(2)).updateStatus(1L, 1);
        verify(wiringPlanService, times(1)).updateStatus(1L, 0);
        verify(wiringPlanService, times(3)).updateStatus(eq(1L), anyInt());
    }
}
