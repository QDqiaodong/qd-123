package com.factory.security.controller;

import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.WiringPlanExportRowVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WiringPlanController.class)
class WiringPlanExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WiringPlanService wiringPlanService;

    private WiringPlanExportRowVO row(String planName, String scene, String statusText,
                                      String accessoryName, String zoneName,
                                      String quantity, String specUnit) {
        WiringPlanExportRowVO row = new WiringPlanExportRowVO();
        row.setPlanId(1L);
        row.setPlanName(planName);
        row.setScene(scene);
        row.setStatusText(statusText);
        row.setAccessoryName(accessoryName);
        row.setZoneTagName(zoneName);
        row.setQuantityText(quantity);
        row.setSpecUnit(specUnit);
        return row;
    }

    private String expectedChineseFileName() {
        return "布线方案导出_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    }

    @Test
    void exportWithFiltersReturnsCsvWithHeadersRowsAndChineseFileName() throws Exception {
        when(wiringPlanService.listExportRows(eq("监控"), eq(1))).thenReturn(java.util.List.of(
                row("厂区外围监控布线方案", "厂区外围监控", "启用", "RVV电源线", "线缆布线区", "600", "mm²"),
                row("厂区外围监控布线方案", "厂区外围监控", "启用", "防爆摄像头", "监控设备区", "12", "MP")));

        String expectedEncoded = URLEncoder.encode(expectedChineseFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        MvcResult result = mockMvc.perform(get("/wiring-plan/export")
                        .param("keyword", "监控")
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''" + expectedEncoded)))
                .andExpect(header().string("Content-Disposition", containsString("wiring-plan-export-")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // UTF-8 BOM，保证 Excel 打开中文不乱码
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);

        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("方案名称,适用场景,启用状态,配件名称,所属分区,需求数量,规格单位", lines[0].substring(1));
        assertEquals("厂区外围监控布线方案,厂区外围监控,启用,RVV电源线,线缆布线区,600,mm²", lines[1]);
        assertEquals("厂区外围监控布线方案,厂区外围监控,启用,防爆摄像头,监控设备区,12,MP", lines[2]);
    }

    @Test
    void emptyFilterResultExportsHeaderOnlyCsv() throws Exception {
        when(wiringPlanService.listExportRows(eq("不存在的方案"), eq(0)))
                .thenReturn(Collections.emptyList());

        MvcResult result = mockMvc.perform(get("/wiring-plan/export")
                        .param("keyword", "不存在的方案")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        // BOM + 表头一行，无数据行
        assertEquals(1, lines.length);
        assertTrue(lines[0].endsWith("方案名称,适用场景,启用状态,配件名称,所属分区,需求数量,规格单位"));
    }

    @Test
    void exportPassesNoFiltersWhenParamsAbsent() throws Exception {
        when(wiringPlanService.listExportRows(null, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/wiring-plan/export"))
                .andExpect(status().isOk());

        verify(wiringPlanService).listExportRows(null, null);
    }

    @Test
    void repeatedExportRequestsEachSucceed() throws Exception {
        // 重复点击在前端做节流，后端每次请求都独立、幂等返回
        when(wiringPlanService.listExportRows(null, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/wiring-plan/export")).andExpect(status().isOk());
        mockMvc.perform(get("/wiring-plan/export")).andExpect(status().isOk());
        mockMvc.perform(get("/wiring-plan/export")).andExpect(status().isOk());

        verify(wiringPlanService, times(3)).listExportRows(null, null);
    }
}
