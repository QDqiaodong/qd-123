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
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private StockCheckVO confirmedVO() {
        StockCheckVO vo = pendingVO();
        vo.setStatus(1);
        vo.setStatusText("已确认");
        vo.setRecordedCount(2);
        return vo;
    }

    private StockCheckItemVO diffItem(Long id, String name, int book, Integer actual, String diffType) {
        StockCheckItemVO item = new StockCheckItemVO();
        item.setId(id);
        item.setAccessoryId(id);
        item.setAccessoryName(name);
        item.setBookQuantity(book);
        item.setActualQuantity(actual);
        item.setRecorded(true);
        item.setAccessoryDeleted(false);
        if (actual != null) {
            item.setDiffQuantity(actual - book);
        }
        item.setDiffType(diffType);
        return item;
    }

    @Test
    void pageReturnsChecks() throws Exception {
        Page<StockCheckVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(pendingVO()));
        when(stockCheckService.page(eq(1), eq(10), eq(0), eq(2L), eq(false), eq(null))).thenReturn(page);

        mockMvc.perform(get("/stock-check/page").param("status", "0").param("zoneTagId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].checkNo").value("PD20260912100000001"))
                .andExpect(jsonPath("$.data.records[0].statusText").value("待确认"));

        verify(stockCheckService).page(1, 10, 0, 2L, false, null);
    }

    @Test
    void pageWithUnassignedFlagQueriesNullZone() throws Exception {
        Page<StockCheckVO> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());
        when(stockCheckService.page(eq(1), eq(10), eq(null), eq(null), eq(true), eq(null))).thenReturn(page);

        mockMvc.perform(get("/stock-check/page").param("unassigned", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stockCheckService).page(1, 10, null, null, true, null);
    }

    @Test
    void pageWithHasRemarkFlagPassesItThrough() throws Exception {
        Page<StockCheckVO> page = new Page<>(1, 10, 0);
        page.setRecords(Collections.emptyList());
        when(stockCheckService.page(eq(1), eq(10), eq(1), eq(null), eq(false), eq(true))).thenReturn(page);

        // 按“已确认且有差异说明”筛选
        mockMvc.perform(get("/stock-check/page")
                        .param("status", "1")
                        .param("hasRemark", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(stockCheckService).page(1, 10, 1, null, false, true);
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
    void confirmWithoutBodyRejectedByRequiredRemark() throws Exception {
        // 差异说明必填：不带 body 时服务层兜底拒绝（模拟真实服务抛错），返回业务错误且不回写
        doThrow(new RuntimeException("请填写差异说明后再确认盘点回写"))
                .when(stockCheckService).confirm(eq(10L), any());

        mockMvc.perform(put("/stock-check/10/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请填写差异说明后再确认盘点回写"));
    }

    @Test
    void confirmWithBlankRemarkRejected() throws Exception {
        // 纯空白说明同样不能确认，返回 400 与明确提示
        mockMvc.perform(put("/stock-check/10/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmRemark\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请填写差异说明后再确认盘点回写"));

        org.mockito.Mockito.verifyNoInteractions(stockCheckService);
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

    @Test
    void diffExportWritesDiffRowsAndTotalRowWithChineseFileName() throws Exception {
        StockCheckDetailVO detail = new StockCheckDetailVO();
        detail.setHeader(confirmedVO());
        StockCheckItemVO loss = diffItem(1001L, "超五类网线", 1000, 980, "loss");
        StockCheckItemVO gain = diffItem(1002L, "六类网线", 600, 650, "gain");
        StockCheckItemVO even = diffItem(1003L, "水晶头", 100, 100, "even");
        StockCheckItemVO deleted = diffItem(1004L, "旧型号", 10, null, "deleted");
        deleted.setAccessoryDeleted(true);
        deleted.setRecorded(false);
        detail.setItems(List.of(loss, gain, even, deleted));
        detail.setRecordedCount(3);
        detail.setDiffCount(2);
        detail.setGainCount(1);
        detail.setLossCount(1);
        detail.setTotalDiffQuantity(30);
        when(stockCheckService.getConfirmedDetailForExport(10L)).thenReturn(detail);

        String expectedFileName = "盘点差异明细_PD20260912100000001.csv";
        String expectedEncoded = URLEncoder.encode(expectedFileName, StandardCharsets.UTF_8).replace("+", "%20");

        MvcResult result = mockMvc.perform(get("/stock-check/10/diff-export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        containsString("filename*=UTF-8''" + expectedEncoded)))
                .andExpect(header().string("Content-Disposition",
                        containsString("stock-check-diff-PD20260912100000001.csv")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        // UTF-8 BOM，Excel 打开中文不乱码
        assertEquals((byte) 0xEF, body[0]);
        assertEquals((byte) 0xBB, body[1]);
        assertEquals((byte) 0xBF, body[2]);

        String csv = new String(body, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("配件名称,账面数,实盘数,盈亏件数", lines[0].substring(1));
        // 只导出盘亏/盘盈行，账实一致与已删除配件不出现
        assertEquals("超五类网线,1000,980,-20", lines[1]);
        assertEquals("六类网线,600,650,+50", lines[2]);
        // 合计行：差异种数与盈亏件数与详情页汇总一致
        assertEquals("合计（差异2种）,,,+30", lines[3]);
        assertEquals(4, lines.length);
    }

    @Test
    void diffExportWithNoDiffWritesHeaderOnly() throws Exception {
        StockCheckDetailVO detail = new StockCheckDetailVO();
        detail.setHeader(confirmedVO());
        // 全部账实一致：差异明细为空分区/无差异，导出只有表头，不追加合计行
        detail.setItems(List.of(diffItem(1001L, "超五类网线", 1000, 1000, "even")));
        detail.setRecordedCount(1);
        detail.setDiffCount(0);
        detail.setGainCount(0);
        detail.setLossCount(0);
        detail.setTotalDiffQuantity(0);
        when(stockCheckService.getConfirmedDetailForExport(10L)).thenReturn(detail);

        MvcResult result = mockMvc.perform(get("/stock-check/10/diff-export"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertTrue(lines[0].endsWith("配件名称,账面数,实盘数,盈亏件数"));
        assertEquals(1, lines.length);
    }

    @Test
    void diffExportForEmptyConfirmedCheckWritesHeaderOnly() throws Exception {
        StockCheckDetailVO detail = new StockCheckDetailVO();
        detail.setHeader(confirmedVO());
        detail.setItems(Collections.emptyList());
        detail.setRecordedCount(0);
        detail.setDiffCount(0);
        detail.setGainCount(0);
        detail.setLossCount(0);
        detail.setTotalDiffQuantity(0);
        when(stockCheckService.getConfirmedDetailForExport(11L)).thenReturn(detail);

        MvcResult result = mockMvc.perform(get("/stock-check/11/diff-export"))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertTrue(lines[0].endsWith("配件名称,账面数,实盘数,盈亏件数"));
        assertEquals(1, lines.length);
    }

    @Test
    void diffExportRejectsPendingCheckWithReason() throws Exception {
        doThrow(new RuntimeException("待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细"))
                .when(stockCheckService).getConfirmedDetailForExport(10L);

        mockMvc.perform(get("/stock-check/10/diff-export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value(
                        "待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细"));
    }
}
