package com.factory.security.controller;

import com.factory.security.service.AccessoryService;
import com.factory.security.vo.SafetyStockVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessoryController.class)
class SafetyStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessoryService accessoryService;

    private SafetyStockVO row(Long id, String name, Long zoneTagId, String zoneName,
                              boolean unassigned, int stock, int safetyStock, int gap) {
        SafetyStockVO vo = new SafetyStockVO();
        vo.setAccessoryId(id);
        vo.setAccessoryName(name);
        vo.setZoneTagId(zoneTagId);
        vo.setZoneTagName(zoneName);
        vo.setUnassignedZone(unassigned);
        vo.setStockQuantity(stock);
        vo.setSafetyStock(safetyStock);
        vo.setGapQuantity(gap);
        return vo;
    }

    @Test
    void safetyStockReturnsLowItemsIncludingUnassigned() throws Exception {
        when(accessoryService.listSafetyStockShortages()).thenReturn(Arrays.asList(
                row(4L, "六类网线", 2L, "线缆布线区", false, 600, 800, 200),
                row(30L, "未分配低位件", null, null, true, 5, 10, 5)));

        mockMvc.perform(get("/accessory/safety-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].accessoryName").value("六类网线"))
                .andExpect(jsonPath("$.data[0].zoneTagName").value("线缆布线区"))
                .andExpect(jsonPath("$.data[0].stockQuantity").value(600))
                .andExpect(jsonPath("$.data[0].safetyStock").value(800))
                .andExpect(jsonPath("$.data[0].gapQuantity").value(200))
                // 未分配分区的低位配件不能漏，分区名为空但有未分配标识
                .andExpect(jsonPath("$.data[1].accessoryName").value("未分配低位件"))
                .andExpect(jsonPath("$.data[1].unassignedZone").value(true))
                .andExpect(jsonPath("$.data[1].gapQuantity").value(5));

        verify(accessoryService).listSafetyStockShortages();
    }

    @Test
    void safetyStockEmptyReturnsEmptyArray() throws Exception {
        when(accessoryService.listSafetyStockShortages()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/accessory/safety-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
