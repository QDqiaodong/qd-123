package com.factory.security.controller;

import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.StockGapZoneSummaryVO;
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
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WiringPlanController.class)
class StockGapZoneSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WiringPlanService wiringPlanService;

    private StockGapZoneSummaryVO row(Long zoneTagId, String zoneTagName, boolean unassigned,
                                      int shortageAccessoryCount, int gapQuantityTotal) {
        StockGapZoneSummaryVO row = new StockGapZoneSummaryVO();
        row.setZoneTagId(zoneTagId);
        row.setZoneTagName(zoneTagName);
        row.setUnassignedZone(unassigned);
        row.setShortageAccessoryCount(shortageAccessoryCount);
        row.setGapQuantityTotal(gapQuantityTotal);
        return row;
    }

    private String expectedChineseFileName() {
        return "库存缺口分区汇总_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
    }

    @Test
    void zoneSummaryReturnsZoneRowsWithUnassignedRow() throws Exception {
        when(wiringPlanService.listStockGapZoneSummary()).thenReturn(Arrays.asList(
                row(2L, "线缆布线区", false, 1, 100),
                row(5L, "监控设备区", false, 0, 0),
                row(null, "未分配分区", true, 2, 130)));

        mockMvc.perform(get("/wiring-plan/stock-gaps/zone-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].zoneTagName").value("线缆布线区"))
                .andExpect(jsonPath("$.data[0].shortageAccessoryCount").value(1))
                .andExpect(jsonPath("$.data[0].gapQuantityTotal").value(100))
                .andExpect(jsonPath("$.data[2].unassignedZone").value(true))
                .andExpect(jsonPath("$.data[2].zoneTagName").value("未分配分区"));

        verify(wiringPlanService).listStockGapZoneSummary();
    }

    @Test
    void zoneSummaryExportWritesZoneRowsTotalRowAndChineseFileName() throws Exception {
        when(wiringPlanService.listStockGapZoneSummary()).thenReturn(Arrays.asList(
                row(2L, "线缆布线区", false, 1, 100),
                row(null, "未分配分区", true, 2, 130)));

        String expectedEncoded = URLEncoder.encode(expectedChineseFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        MvcResult result = mockMvc.perform(get("/wiring-plan/stock-gaps/zone-summary/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''" + expectedEncoded)))
                .andExpect(header().string("Content-Disposition", containsString("stock-gap-zone-summary-")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // UTF-8 BOM，保证 Excel 打开中文不乱码
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);

        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("分区名称,涉及配件种数,缺口件数", lines[0].substring(1));
        assertEquals("线缆布线区,1,100", lines[1]);
        assertEquals("未分配分区,2,130", lines[2]);
        // 合计行为分区小计之和，与页面汇总表合计行一致
        assertEquals("合计,3,230", lines[3]);
    }

    @Test
    void zoneSummaryExportWithEmptySummaryWritesHeaderAndZeroTotal() throws Exception {
        when(wiringPlanService.listStockGapZoneSummary()).thenReturn(Collections.emptyList());

        MvcResult result = mockMvc.perform(get("/wiring-plan/stock-gaps/zone-summary/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        // BOM + 表头 + 合计 0 行，无分区数据行
        assertEquals(2, lines.length);
        assertTrue(lines[0].endsWith("分区名称,涉及配件种数,缺口件数"));
        assertEquals("合计,0,0", lines[1]);
    }

    @Test
    void repeatedZoneSummaryExportRequestsEachSucceed() throws Exception {
        // 重复点击在前端做节流，后端每次请求都独立、幂等返回
        when(wiringPlanService.listStockGapZoneSummary()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/wiring-plan/stock-gaps/zone-summary/export")).andExpect(status().isOk());
        mockMvc.perform(get("/wiring-plan/stock-gaps/zone-summary/export")).andExpect(status().isOk());

        verify(wiringPlanService, times(2)).listStockGapZoneSummary();
    }
}
