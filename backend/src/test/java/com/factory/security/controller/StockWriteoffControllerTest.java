package com.factory.security.controller;

import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.StockGapVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WiringPlanController.class)
class StockWriteoffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WiringPlanService wiringPlanService;

    @Test
    void stockGapsReturnsRows() throws Exception {
        StockGapVO row = new StockGapVO();
        row.setAccessoryId(100L);
        row.setAccessoryName("RVV电源线");
        row.setStockQuantity(500);
        row.setRequiredQuantity(600);
        row.setGapQuantity(100);
        row.setShortage(true);
        when(wiringPlanService.listStockGaps()).thenReturn(List.of(row));

        mockMvc.perform(get("/wiring-plan/stock-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].accessoryName").value("RVV电源线"))
                .andExpect(jsonPath("$.data[0].shortage").value(true))
                .andExpect(jsonPath("$.data[0].gapQuantity").value(100));

        verify(wiringPlanService).listStockGaps();
    }

    @Test
    void writeoffSuccessReturnsSuccess() throws Exception {
        when(wiringPlanService.writeoff(1L)).thenReturn(true);

        mockMvc.perform(put("/wiring-plan/1/writeoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wiringPlanService).writeoff(1L);
    }

    @Test
    void writeoffRejectedForAlreadyWrittenOffPlan() throws Exception {
        when(wiringPlanService.writeoff(1L))
                .thenThrow(new RuntimeException("该方案已核销出库，同一方案不可重复核销"));

        mockMvc.perform(put("/wiring-plan/1/writeoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该方案已核销出库，同一方案不可重复核销"));
    }

    @Test
    void stockGapsEmptyReturnsEmptyData() throws Exception {
        when(wiringPlanService.listStockGaps()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/wiring-plan/stock-gaps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(wiringPlanService, never()).writeoff(org.mockito.ArgumentMatchers.anyLong());
    }
}
