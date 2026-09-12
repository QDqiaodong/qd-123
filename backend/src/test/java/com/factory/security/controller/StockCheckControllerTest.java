package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.service.StockCheckService;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckItemVO;
import com.factory.security.vo.StockCheckVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockCheckController.class)
class StockCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockCheckService stockCheckService;

    private StockCheckVO pendingVO() {
        StockCheckVO vo = new StockCheckVO();
        vo.setId(10L);
        vo.setCheckNo("PD20260912100000001");
        vo.setZoneTagId(2L);
        vo.setZoneName("线缆布线区");
        vo.setUnassignedZone(false);
        vo.setStatus(0);
        vo.setStatusText("待确认");
        vo.setItemCount(2);
        vo.setRecordedCount(1);
        vo.setDiffCount(1);
        return vo;
    }

    @Test
    void pageReturnsChecks() throws Exception {
        Page<StockCheckVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(pendingVO()));
        when(stockCheckService.page(eq(1), eq(10), eq(0), eq(2L), eq(false))).thenReturn(page);

        mockMvc.perform(get("/stock-check/page").param("status", "0").param("zoneTagId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].checkNo").value("PD20260912100000001"))
                .andExpect(jsonPath("$.data.records[0].statusText").value("待确认"));

        verify(stockCheckService).page(1, 10, 0, 2L, false);
    }

    @Test
    void pageWithUnassignedFlagQueriesNullZone() throws Exception {
        Page<StockCheckVO> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());
        when(stockCheckService.page(eq(1), eq(10), eq(null), eq(null), eq(true))).thenReturn(page);

        mockMvc.perform(get("/stock-check/page").param("unassigned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stockCheckService).page(1, 10, null, null, true);
    }

    @Test
    void detailReturnsHeaderAndItems() throws Exception {
        StockCheckDetailVO detail = new StockCheckDetailVO();
        detail.setHeader(pendingVO());
        StockCheckItemVO item = new StockCheckItemVO();
        item.setId(1001L);
        item.setAccessoryName("超五类网线");
        item.setBookQuantity(1000);
        item.setActualQuantity(980);
        item.setDiffQuantity(-20);
        item.setDiffType("loss");
        item.setAccessoryDeleted(false);
        detail.setItems(List.of(item));
        when(stockCheckService.getDetailById(10L)).thenReturn(detail);

        mockMvc.perform(get("/stock-check/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.checkNo").value("PD20260912100000001"))
                .andExpect(jsonPath("$.data.items[0].accessoryName").value("超五类网线"))
                .andExpect(jsonPath("$.data.items[0].diffQuantity").value(-20));
    }

    @Test
    void createReturnsNewCheckId() throws Exception {
        when(stockCheckService.create(any(StockCheckCreateDTO.class))).thenReturn(55L);

        mockMvc.perform(post("/stock-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneTagId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(55));
    }

    @Test
    void recordItemsSuccess() throws Exception {
        mockMvc.perform(put("/stock-check/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"actualQuantity\":980}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stockCheckService).recordItems(eq(10L), any(StockCheckItemDTO.class));
    }

    @Test
    void recordItemsRejectsNegativeQuantity() throws Exception {
        mockMvc.perform(put("/stock-check/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"actualQuantity\":-1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void recordItemsRejectsEmptyItems() throws Exception {
        mockMvc.perform(put("/stock-check/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void confirmSuccess() throws Exception {
        mockMvc.perform(put("/stock-check/10/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmRemark\":\"月度盘点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stockCheckService).confirm(eq(10L), any(StockCheckConfirmDTO.class));
    }

    @Test
    void confirmWithoutBodyAlsoWorks() throws Exception {
        mockMvc.perform(put("/stock-check/10/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void confirmedCheckRejectedWhenModify() throws Exception {
        doThrow(new RuntimeException("该盘点单已确认并回写库存，单据已锁定不可修改"))
                .when(stockCheckService).recordItems(eq(10L), any(StockCheckItemDTO.class));

        mockMvc.perform(put("/stock-check/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"actualQuantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该盘点单已确认并回写库存，单据已锁定不可修改"));
    }

    @Test
    void deletePendingSuccess() throws Exception {
        mockMvc.perform(delete("/stock-check/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(stockCheckService).delete(10L);
    }
}
