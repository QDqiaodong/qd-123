package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.WiringPlanVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void writtenOffPlanStatusChangeReturnsClearError() throws Exception {
        // 已核销出库的方案启用状态锁定，停用请求返回明确错误，前端开关恢复原状态
        when(wiringPlanService.updateStatus(1L, 0))
                .thenThrow(new RuntimeException("该方案已核销出库，现存量已扣减，不可变更启用状态"));

        mockMvc.perform(put("/wiring-plan/1/status").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该方案已核销出库，现存量已扣减，不可变更启用状态"));
    }

    @Test
    void pageResponseCarriesNoStoreCacheHeader() throws Exception {
        // 库存校验为实时结果，方案列表响应不得被浏览器/代理缓存，否则改完现存量重开仍是旧“充足/不足”
        Page<WiringPlanVO> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(wiringPlanService.page(anyInt(), anyInt(), isNull(), isNull())).thenReturn(emptyPage);

        mockMvc.perform(get("/wiring-plan/page"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));
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
