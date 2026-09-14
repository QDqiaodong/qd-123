package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.FiberSpliceCommissionDTO;
import com.factory.security.dto.FiberSpliceJointCreateDTO;
import com.factory.security.dto.FiberSpliceJointUpdateDTO;
import com.factory.security.dto.FiberSpliceVoidDTO;
import com.factory.security.service.FiberSpliceJointService;
import com.factory.security.vo.FiberSpliceJointVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FiberSpliceJointController.class)
class FiberSpliceJointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FiberSpliceJointService fiberSpliceJointService;

    private FiberSpliceJointVO vo(Long id, String no, int otdr, int commissionable, int status) {
        FiberSpliceJointVO vo = new FiberSpliceJointVO();
        vo.setId(id);
        vo.setSpliceNo(no);
        vo.setZoneTagId(3L);
        vo.setZoneName("接头终端区");
        vo.setZoneDeleted(false);
        vo.setReserveMeters(15);
        vo.setOtdrPassed(otdr);
        vo.setOtdrPassedText(otdr == 1 ? "已过 OTDR" : "未过 OTDR");
        vo.setCommissionable(commissionable);
        vo.setCommissionableText(commissionable == 1 ? "可投运" : "不可投运");
        vo.setStatus(status);
        vo.setStatusText(status == 1 ? "已作废" : "在档");
        return vo;
    }

    @Test
    void pageFiltersByZone() throws Exception {
        Page<FiberSpliceJointVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo(1L, "RJ-001", 1, 1, 0)));
        when(fiberSpliceJointService.page(eq(1), eq(10), eq("RJ"), eq(3L),
                eq(1), eq(1), eq(0))).thenReturn(page);

        mockMvc.perform(get("/fiber-splice/page")
                        .param("keyword", "RJ")
                        .param("zoneTagId", "3")
                        .param("otdrPassed", "1")
                        .param("commissionable", "1")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].spliceNo").value("RJ-001"))
                .andExpect(jsonPath("$.data.records[0].zoneName").value("接头终端区"))
                .andExpect(jsonPath("$.data.records[0].otdrPassedText").value("已过 OTDR"))
                .andExpect(jsonPath("$.data.records[0].commissionableText").value("可投运"));

        verify(fiberSpliceJointService).page(1, 10, "RJ", 3L, 1, 1, 0);
    }

    @Test
    void createReturnsNewId() throws Exception {
        when(fiberSpliceJointService.create(any(FiberSpliceJointCreateDTO.class))).thenReturn(88L);

        mockMvc.perform(post("/fiber-splice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spliceNo\":\"RJ-001\",\"zoneTagId\":3,\"reserveMeters\":12,\"otdrPassed\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(88));
    }

    @Test
    void createRejectsBlankSpliceNo() throws Exception {
        mockMvc.perform(post("/fiber-splice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spliceNo\":\"   \",\"zoneTagId\":3,\"reserveMeters\":12,\"otdrPassed\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsMissingZone() throws Exception {
        mockMvc.perform(post("/fiber-splice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spliceNo\":\"RJ-001\",\"reserveMeters\":12,\"otdrPassed\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsNegativeMeters() throws Exception {
        mockMvc.perform(post("/fiber-splice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spliceNo\":\"RJ-001\",\"zoneTagId\":3,\"reserveMeters\":-1,\"otdrPassed\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateSpliceNoReturnsBusinessMessage() throws Exception {
        when(fiberSpliceJointService.create(any(FiberSpliceJointCreateDTO.class)))
                .thenThrow(new RuntimeException("接头编号「RJ-001」已登记，同一接头编号不能登记两次"));

        mockMvc.perform(post("/fiber-splice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spliceNo\":\"RJ-001\",\"zoneTagId\":3,\"reserveMeters\":12,\"otdrPassed\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("接头编号「RJ-001」已登记，同一接头编号不能登记两次"));
    }

    @Test
    void updateSuccess() throws Exception {
        doNothing().when(fiberSpliceJointService)
                .update(eq(1L), any(FiberSpliceJointUpdateDTO.class));

        mockMvc.perform(put("/fiber-splice/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneTagId\":2,\"reserveMeters\":20,\"otdrPassed\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fiberSpliceJointService).update(eq(1L), any(FiberSpliceJointUpdateDTO.class));
    }

    @Test
    void commissionSuccessForPassedOtdr() throws Exception {
        doNothing().when(fiberSpliceJointService)
                .commission(eq(1L), any(FiberSpliceCommissionDTO.class));

        mockMvc.perform(put("/fiber-splice/1/commission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionable\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fiberSpliceJointService).commission(eq(1L), any(FiberSpliceCommissionDTO.class));
    }

    @Test
    void commissionRejectedWhenOtdrNotPassed() throws Exception {
        doThrow(new RuntimeException("该接头尚未通过 OTDR，不能标记可投运；请先补做 OTDR 测试"))
                .when(fiberSpliceJointService).commission(eq(2L), any(FiberSpliceCommissionDTO.class));

        mockMvc.perform(put("/fiber-splice/2/commission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionable\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message")
                        .value("该接头尚未通过 OTDR，不能标记可投运；请先补做 OTDR 测试"));
    }

    @Test
    void commissionRejectsMissingFlag() throws Exception {
        mockMvc.perform(put("/fiber-splice/1/commission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void voidSuccess() throws Exception {
        doNothing().when(fiberSpliceJointService)
                .voidJoint(eq(1L), any(FiberSpliceVoidDTO.class));

        mockMvc.perform(put("/fiber-splice/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"熔接质量异常，重接后另登记新编号\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(fiberSpliceJointService).voidJoint(eq(1L), any(FiberSpliceVoidDTO.class));
    }

    @Test
    void voidWithoutBodyAlsoAccepted() throws Exception {
        doNothing().when(fiberSpliceJointService)
                .voidJoint(eq(1L), any(FiberSpliceVoidDTO.class));

        mockMvc.perform(put("/fiber-splice/1/void"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateVoidedJointRejected() throws Exception {
        doThrow(new RuntimeException("该接头已作废留档，不能再编辑"))
                .when(fiberSpliceJointService).update(eq(9L), any(FiberSpliceJointUpdateDTO.class));

        mockMvc.perform(put("/fiber-splice/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zoneTagId\":2,\"reserveMeters\":20,\"otdrPassed\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该接头已作废留档，不能再编辑"));
    }
}
