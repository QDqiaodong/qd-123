package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.ReplenishCancelDTO;
import com.factory.security.dto.ReplenishCreateDTO;
import com.factory.security.dto.ReplenishItemDTO;
import com.factory.security.service.ReplenishOrderService;
import com.factory.security.vo.ReplenishOrderDetailVO;
import com.factory.security.vo.ReplenishOrderItemVO;
import com.factory.security.vo.ReplenishOrderVO;
import com.factory.security.vo.ReplenishZoneSummaryVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

@WebMvcTest(ReplenishOrderController.class)
class ReplenishOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReplenishOrderService replenishOrderService;

    private ReplenishOrderVO draftVO() {
        ReplenishOrderVO vo = new ReplenishOrderVO();
        vo.setId(10L);
        vo.setReplenishNo("BH20260912100000001");
        vo.setStatus(0);
        vo.setStatusText("待提交");
        vo.setItemCount(2);
        vo.setTotalQuantity(220);
        vo.setZoneCount(2);
        return vo;
    }

    @Test
    void pageReturnsOrders() throws Exception {
        Page<ReplenishOrderVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(draftVO()));
        when(replenishOrderService.page(eq(1), eq(10), eq(0))).thenReturn(page);

        mockMvc.perform(get("/replenish-order/page").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].replenishNo").value("BH20260912100000001"))
                .andExpect(jsonPath("$.data.records[0].statusText").value("待提交"))
                .andExpect(jsonPath("$.data.records[0].totalQuantity").value(220));

        verify(replenishOrderService).page(1, 10, 0);
    }

    @Test
    void detailReturnsHeaderItemsAndZoneSummary() throws Exception {
        ReplenishOrderDetailVO detail = new ReplenishOrderDetailVO();
        detail.setHeader(draftVO());
        ReplenishOrderItemVO item = new ReplenishOrderItemVO();
        item.setId(1001L);
        item.setAccessoryId(4L);
        item.setAccessoryName("六类网线");
        item.setGapQuantity(200);
        item.setReplenishQuantity(200);
        item.setAccessoryDeleted(false);
        item.setPendingConflict(false);
        detail.setItems(List.of(item));
        ReplenishZoneSummaryVO zoneSummary = new ReplenishZoneSummaryVO();
        zoneSummary.setZoneName("线缆布线区");
        zoneSummary.setAccessoryCount(1);
        zoneSummary.setTotalQuantity(200);
        detail.setZoneSummaries(List.of(zoneSummary));
        detail.setConflictCount(0);
        when(replenishOrderService.getDetailById(10L)).thenReturn(detail);

        mockMvc.perform(get("/replenish-order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.header.replenishNo").value("BH20260912100000001"))
                .andExpect(jsonPath("$.data.items[0].accessoryName").value("六类网线"))
                .andExpect(jsonPath("$.data.zoneSummaries[0].totalQuantity").value(200));
    }

    @Test
    void createReturnsNewOrderId() throws Exception {
        when(replenishOrderService.create(any(ReplenishCreateDTO.class))).thenReturn(77L);

        mockMvc.perform(post("/replenish-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"accessoryId\":4},{\"accessoryId\":7,\"replenishQuantity\":30}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/replenish-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsNonPositiveQuantity() throws Exception {
        mockMvc.perform(post("/replenish-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"accessoryId\":4,\"replenishQuantity\":0}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateItemsSuccess() throws Exception {
        mockMvc.perform(put("/replenish-order/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"replenishQuantity\":180}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(replenishOrderService).updateItems(eq(10L), any(ReplenishItemDTO.class));
    }

    @Test
    void updateItemsRejectsNegativeQuantity() throws Exception {
        mockMvc.perform(put("/replenish-order/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"replenishQuantity\":-1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void submitSuccess() throws Exception {
        mockMvc.perform(put("/replenish-order/10/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(replenishOrderService).submit(10L);
    }

    @Test
    void cancelSuccess() throws Exception {
        mockMvc.perform(put("/replenish-order/10/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelReason\":\"采购计划调整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(replenishOrderService).cancel(eq(10L), any(ReplenishCancelDTO.class));
    }

    @Test
    void cancelWithoutBodyAlsoWorks() throws Exception {
        mockMvc.perform(put("/replenish-order/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteDraftSuccess() throws Exception {
        mockMvc.perform(delete("/replenish-order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(replenishOrderService).delete(10L);
    }

    @Test
    void submittedOrderRejectsQuantityChange() throws Exception {
        doThrow(new RuntimeException("该补货单已提交，补货数量已锁定不可修改"))
                .when(replenishOrderService).updateItems(eq(10L), any(ReplenishItemDTO.class));

        mockMvc.perform(put("/replenish-order/10/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":1001,\"replenishQuantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该补货单已提交，补货数量已锁定不可修改"));
    }

    @Test
    void submitRejectsConflictWithReason() throws Exception {
        doThrow(new RuntimeException("配件「六类网线」已在补货单「BH20260912090000009」待补中，请先作废冲突单或删除本行后再提交"))
                .when(replenishOrderService).submit(10L);

        mockMvc.perform(put("/replenish-order/10/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(
                        "配件「六类网线」已在补货单「BH20260912090000009」待补中，请先作废冲突单或删除本行后再提交"));
    }
}
