package com.factory.security.controller;

import com.factory.security.service.AccessoryService;
import com.factory.security.vo.SafetyStockVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessoryController.class)
class SafetyStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessoryService accessoryService;

    private SafetyStockVO row(Long id, String name, Long zoneTagId, String zoneName,
                              boolean unassigned, int stock, int safetyStock, int gap,
                              boolean urgent) {
        SafetyStockVO vo = new SafetyStockVO();
        vo.setAccessoryId(id);
        vo.setAccessoryName(name);
        vo.setZoneTagId(zoneTagId);
        vo.setZoneTagName(zoneName);
        vo.setUnassignedZone(unassigned);
        vo.setStockQuantity(stock);
        vo.setSafetyStock(safetyStock);
        vo.setGapQuantity(gap);
        vo.setUrgent(urgent);
        return vo;
    }

    @Test
    void safetyStockReturnsLowItemsIncludingUnassigned() throws Exception {
        when(accessoryService.listSafetyStockShortages(null, false)).thenReturn(Arrays.asList(
                row(4L, "六类网线", 2L, "线缆布线区", false, 600, 800, 200, false),
                row(30L, "未分配急件", null, null, true, 1, 10, 9, true)));

        mockMvc.perform(get("/accessory/safety-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].accessoryName").value("六类网线"))
                .andExpect(jsonPath("$.data[0].zoneTagName").value("线缆布线区"))
                .andExpect(jsonPath("$.data[0].stockQuantity").value(600))
                .andExpect(jsonPath("$.data[0].safetyStock").value(800))
                .andExpect(jsonPath("$.data[0].gapQuantity").value(200))
                // 缺口 200 不足下限 800 的一半：非紧急
                .andExpect(jsonPath("$.data[0].urgent").value(false))
                // 未分配分区的低位配件不能漏，分区名为空但有未分配标识
                .andExpect(jsonPath("$.data[1].accessoryName").value("未分配急件"))
                .andExpect(jsonPath("$.data[1].unassignedZone").value(true))
                .andExpect(jsonPath("$.data[1].gapQuantity").value(9))
                // 未分配分区的急件同样带紧急标记
                .andExpect(jsonPath("$.data[1].urgent").value(true));

        verify(accessoryService).listSafetyStockShortages(null, false);
    }

    @Test
    void safetyStockFiltersByZoneTagId() throws Exception {
        when(accessoryService.listSafetyStockShortages(2L, false)).thenReturn(Collections.singletonList(
                row(4L, "六类网线", 2L, "线缆布线区", false, 600, 800, 200, false)));

        mockMvc.perform(get("/accessory/safety-stock").param("zoneTagId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].zoneTagName").value("线缆布线区"));

        verify(accessoryService).listSafetyStockShortages(2L, false);
    }

    @Test
    void safetyStockFiltersUnassignedZoneOnly() throws Exception {
        when(accessoryService.listSafetyStockShortages(null, true)).thenReturn(Collections.singletonList(
                row(30L, "未分配急件", null, null, true, 1, 10, 9, true)));

        mockMvc.perform(get("/accessory/safety-stock").param("unassignedZone", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unassignedZone").value(true))
                .andExpect(jsonPath("$.data[0].urgent").value(true));

        verify(accessoryService).listSafetyStockShortages(null, true);
    }

    @Test
    void safetyStockEmptyReturnsEmptyArray() throws Exception {
        when(accessoryService.listSafetyStockShortages(null, false)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/accessory/safety-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void safetyStockExportWritesFilteredRowsAndChineseFileName() throws Exception {
        when(accessoryService.listSafetyStockShortages(2L, false)).thenReturn(Arrays.asList(
                row(4L, "六类网线", 2L, "线缆布线区", false, 600, 800, 200, false),
                row(30L, "未分配急件", null, null, true, 1, 10, 9, true)));

        String expectedFileName = "安全库存台账_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        String expectedEncoded = URLEncoder.encode(expectedFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        MvcResult result = mockMvc.perform(get("/accessory/safety-stock/export")
                        .param("zoneTagId", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''" + expectedEncoded)))
                .andExpect(header().string("Content-Disposition",
                        containsString("safety-stock-")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // UTF-8 BOM，保证 Excel 打开中文不乱码
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);

        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        // 列与页面一致：名称、分区、现存、下限、缺口、紧急
        assertEquals("名称,分区,现存量,下限,缺口,紧急", lines[0].substring(1));
        assertEquals("六类网线,线缆布线区,600,800,200,", lines[1]);
        // 未分配分区在导出文件中单独写作“未分配分区”，不允许出现空分区；紧急行末列写“紧急”
        assertEquals("未分配急件,未分配分区,1,10,9,紧急", lines[2]);
        // 筛选参数与页面台账同源透传
        verify(accessoryService).listSafetyStockShortages(2L, false);
    }

    @Test
    void safetyStockExportUnassignedOnlyWritesUnassignedRows() throws Exception {
        when(accessoryService.listSafetyStockShortages(null, true)).thenReturn(Collections.singletonList(
                row(30L, "未分配急件", null, null, true, 1, 10, 9, true)));

        MvcResult result = mockMvc.perform(get("/accessory/safety-stock/export")
                        .param("unassignedZone", "true"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("名称,分区,现存量,下限,缺口,紧急", lines[0].substring(1));
        assertEquals("未分配急件,未分配分区,1,10,9,紧急", lines[1]);
        verify(accessoryService).listSafetyStockShortages(null, true);
    }

    @Test
    void safetyStockExportWithEmptyResultWritesHeaderOnly() throws Exception {
        when(accessoryService.listSafetyStockShortages(99L, false)).thenReturn(Collections.emptyList());

        MvcResult result = mockMvc.perform(get("/accessory/safety-stock/export")
                        .param("zoneTagId", "99"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // limit=-1 保留末尾空串：BOM + 表头 + CRLF，无数据行，共 2 段
        String[] lines = csv.split("\r\n", -1);
        assertEquals(2, lines.length);
        assertTrue(csv.endsWith("名称,分区,现存量,下限,缺口,紧急\r\n"));
    }
}
