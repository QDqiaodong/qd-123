package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.service.CableReelService;
import com.factory.security.vo.CableReelVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CableReelController.class)
class CableReelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CableReelService cableReelService;

    private CableReelVO vo(Long id, String reelNo, int remaining, int status, Boolean matched) {
        CableReelVO vo = new CableReelVO();
        vo.setId(id);
        vo.setReelNo(reelNo);
        vo.setAccessoryId(5L);
        vo.setAccessoryName("RVV电源线");
        vo.setModel("RVV-2*1.0");
        vo.setSpecUnit("m");
        vo.setRemainingMeters(remaining);
        vo.setStatus(status);
        vo.setStatusText(status == 1 ? "已开盘" : "未开盘");
        vo.setAccessoryStockQuantity(remaining);
        vo.setAccessoryDeleted(false);
        vo.setStockMatched(matched);
        return vo;
    }

    @Test
    void pageReturnsReels() throws Exception {
        Page<CableReelVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo(1L, "P-001", 170, 1, true)));
        when(cableReelService.page(eq(1), eq(10), eq("P"), eq(1), eq(5L))).thenReturn(page);

        mockMvc.perform(get("/cable-reel/page")
                        .param("keyword", "P")
                        .param("status", "1")
                        .param("accessoryId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].reelNo").value("P-001"))
                .andExpect(jsonPath("$.data.records[0].statusText").value("已开盘"))
                .andExpect(jsonPath("$.data.records[0].stockMatched").value(true));

        verify(cableReelService).page(1, 10, "P", 1, 5L);
    }

    @Test
    void createReturnsNewReelId() throws Exception {
        when(cableReelService.create(any(CableReelCreateDTO.class))).thenReturn(77L);

        mockMvc.perform(post("/cable-reel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reelNo\":\"P-001\",\"accessoryId\":5,\"remainingMeters\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    void createRejectsBlankReelNo() throws Exception {
        mockMvc.perform(post("/cable-reel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reelNo\":\"   \",\"accessoryId\":5,\"remainingMeters\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsMissingAccessory() throws Exception {
        mockMvc.perform(post("/cable-reel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reelNo\":\"P-001\",\"remainingMeters\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsNegativeMeters() throws Exception {
        mockMvc.perform(post("/cable-reel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reelNo\":\"P-001\",\"accessoryId\":5,\"remainingMeters\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateReelNoReturnsBusinessMessage() throws Exception {
        when(cableReelService.create(any(CableReelCreateDTO.class)))
                .thenThrow(new RuntimeException("盘号「P-001」已建档，同一盘号不能建两次"));

        mockMvc.perform(post("/cable-reel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reelNo\":\"P-001\",\"accessoryId\":5,\"remainingMeters\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("盘号「P-001」已建档，同一盘号不能建两次"));
    }

    @Test
    void openSuccess() throws Exception {
        doNothing().when(cableReelService).open(1L);

        mockMvc.perform(put("/cable-reel/1/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(cableReelService).open(1L);
    }

    @Test
    void deductSuccess() throws Exception {
        doNothing().when(cableReelService).deduct(eq(1L), any(CableReelDeductDTO.class));

        mockMvc.perform(put("/cable-reel/1/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meters\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(cableReelService).deduct(eq(1L), any(CableReelDeductDTO.class));
    }

    @Test
    void deductRejectsZeroMeters() throws Exception {
        mockMvc.perform(put("/cable-reel/1/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meters\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deductUnopenedReturnsBusinessMessage() throws Exception {
        doThrow(new RuntimeException("该盘尚未开盘确认，不能扣米，请先开盘确认"))
                .when(cableReelService).deduct(eq(2L), any(CableReelDeductDTO.class));

        mockMvc.perform(put("/cable-reel/2/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"meters\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该盘尚未开盘确认，不能扣米，请先开盘确认"));
    }

    @Test
    void deleteSuccessForUnopened() throws Exception {
        doNothing().when(cableReelService).delete(2L);

        mockMvc.perform(delete("/cable-reel/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteOpenedRejected() throws Exception {
        doThrow(new RuntimeException("该盘已开盘确认，米数已计入配件档案，不能删除"))
                .when(cableReelService).delete(1L);

        mockMvc.perform(delete("/cable-reel/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该盘已开盘确认，米数已计入配件档案，不能删除"));
    }
}
