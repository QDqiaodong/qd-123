package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.InspectionCreateDTO;
import com.factory.security.dto.InspectionQualifyDTO;
import com.factory.security.dto.InspectionResultDTO;
import com.factory.security.service.InspectionOrderService;
import com.factory.security.vo.InspectionOrderVO;
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

@WebMvcTest(InspectionOrderController.class)
class InspectionOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InspectionOrderService inspectionOrderService;

    private InspectionOrderVO vo(Long id, String no, int returned, int qualified, String conclusion) {
        InspectionOrderVO vo = new InspectionOrderVO();
        vo.setId(id);
        vo.setInspectionNo(no);
        vo.setAccessoryId(11L);
        vo.setAccessoryName("防爆摄像头");
        vo.setModel("DS-2CD3T46");
        vo.setBatchNo("B2026-09");
        vo.setLabName("厂区中心实验室");
        vo.setSampleReturned(returned);
        vo.setSampleReturnedText(returned == 1 ? "已回样" : "待回样");
        vo.setQualified(qualified);
        vo.setQualifiedText(qualified == 1 ? "合格" : "未判定合格");
        vo.setLabConclusion(conclusion);
        return vo;
    }

    @Test
    void pageFiltersBySampleReturned() throws Exception {
        Page<InspectionOrderVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo(1L, "SJ20260914100000001", 1, 1, "检测合格")));
        when(inspectionOrderService.page(eq(1), eq(10), eq(1), eq(1), eq("防爆")))
                .thenReturn(page);

        mockMvc.perform(get("/inspection-order/page")
                        .param("sampleReturned", "1")
                        .param("qualified", "1")
                        .param("keyword", "防爆"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].inspectionNo")
                        .value("SJ20260914100000001"))
                .andExpect(jsonPath("$.data.records[0].sampleReturnedText").value("已回样"))
                .andExpect(jsonPath("$.data.records[0].labConclusion").value("检测合格"));

        verify(inspectionOrderService).page(1, 10, 1, 1, "防爆");
    }

    @Test
    void createReturnsNewId() throws Exception {
        when(inspectionOrderService.create(any(InspectionCreateDTO.class))).thenReturn(77L);

        mockMvc.perform(post("/inspection-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessoryId\":11,\"batchNo\":\"B2026-09\",\"labName\":\"厂区中心实验室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(77));
    }

    @Test
    void createRejectsBlankBatch() throws Exception {
        mockMvc.perform(post("/inspection-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessoryId\":11,\"batchNo\":\"   \",\"labName\":\"厂区中心实验室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsMissingLab() throws Exception {
        mockMvc.perform(post("/inspection-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessoryId\":11,\"batchNo\":\"B2026-09\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createRejectsMissingAccessory() throws Exception {
        mockMvc.perform(post("/inspection-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"B2026-09\",\"labName\":\"厂区中心实验室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deletedAccessoryReturnsBusinessMessage() throws Exception {
        when(inspectionOrderService.create(any(InspectionCreateDTO.class)))
                .thenThrow(new RuntimeException("送检配件不存在或已删除，请刷新配件列表后重新选择"));

        mockMvc.perform(post("/inspection-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessoryId\":999,\"batchNo\":\"B2026-09\",\"labName\":\"厂区中心实验室\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message")
                        .value("送检配件不存在或已删除，请刷新配件列表后重新选择"));
    }

    @Test
    void writeResultSuccess() throws Exception {
        doNothing().when(inspectionOrderService)
                .writeResult(eq(1L), any(InspectionResultDTO.class));

        mockMvc.perform(put("/inspection-order/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labConclusion\":\"外观与电气性能检测合格\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(inspectionOrderService).writeResult(eq(1L), any(InspectionResultDTO.class));
    }

    @Test
    void writeResultRejectsBlankConclusion() throws Exception {
        mockMvc.perform(put("/inspection-order/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labConclusion\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void qualifySuccessForReturnedSample() throws Exception {
        doNothing().when(inspectionOrderService)
                .qualify(eq(1L), any(InspectionQualifyDTO.class));

        mockMvc.perform(put("/inspection-order/1/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qualified\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(inspectionOrderService).qualify(eq(1L), any(InspectionQualifyDTO.class));
    }

    @Test
    void qualifyRejectedWhenSampleNotReturned() throws Exception {
        doThrow(new RuntimeException("该送检单尚未回样，实验室写回结论前不能标记合格"))
                .when(inspectionOrderService).qualify(eq(2L), any(InspectionQualifyDTO.class));

        mockMvc.perform(put("/inspection-order/2/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qualified\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该送检单尚未回样，实验室写回结论前不能标记合格"));
    }

    @Test
    void qualifyRejectsMissingFlag() throws Exception {
        mockMvc.perform(put("/inspection-order/1/qualify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
