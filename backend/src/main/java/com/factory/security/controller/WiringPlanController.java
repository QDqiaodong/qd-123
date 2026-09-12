package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.Result;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.dto.WriteoffDTO;
import com.factory.security.service.WiringPlanService;
import com.factory.security.util.CsvExporter;
import com.factory.security.vo.StockGapVO;
import com.factory.security.vo.StockGapZoneSummaryVO;
import com.factory.security.vo.WiringPlanExportRowVO;
import com.factory.security.vo.WiringPlanVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/wiring-plan")
@CrossOrigin(exposedHeaders = "Content-Disposition")
public class WiringPlanController {

    private static final String[] EXPORT_HEADERS = {
            "方案名称", "适用场景", "启用状态", "配件名称", "所属分区", "需求数量", "规格单位"
    };

    private static final String[] ZONE_SUMMARY_EXPORT_HEADERS = {
            "分区名称", "涉及配件种数", "缺口件数"
    };

    @Autowired
    private WiringPlanService wiringPlanService;

    @GetMapping("/page")
    public Result<Page<WiringPlanVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(wiringPlanService.page(pageNum, pageSize, keyword, status));
    }

    /**
     * 导出当前关键词、启用状态筛选结果。空结果也返回 CSV（仅含表头），由前端先行提示；
     * 文件名包含中文，使用 RFC 5987 filename* 编码并提供 ASCII 兜底名
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws IOException {
        List<WiringPlanExportRowVO> rows = wiringPlanService.listExportRows(keyword, status);

        List<String[]> dataRows = new ArrayList<>();
        for (WiringPlanExportRowVO row : rows) {
            dataRows.add(new String[]{
                    row.getPlanName(),
                    row.getScene(),
                    row.getStatusText(),
                    row.getAccessoryName(),
                    row.getZoneTagName(),
                    row.getQuantityText(),
                    row.getSpecUnit()
            });
        }

        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String chineseFileName = "布线方案导出_" + datePart + ".csv";
        String encodedFileName = URLEncoder.encode(chineseFileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"wiring-plan-export-" + datePart + ".csv\"; "
                        + "filename*=UTF-8''" + encodedFileName);
        // 空结果仍写出仅含表头（含 UTF-8 BOM）的文件
        CsvExporter.write(response.getOutputStream(), EXPORT_HEADERS, dataRows);
    }

    @GetMapping("/{id}")
    public Result<WiringPlanVO> getById(@PathVariable Long id) {
        return Result.success(wiringPlanService.getDetailById(id));
    }

    /**
     * 库存缺口列表：需求合计只统计已启用且未核销的方案。
     * 必须声明在 /{id} 之前，避免 stock-gaps 被当作方案 ID 匹配
     */
    @GetMapping("/stock-gaps")
    public Result<List<StockGapVO>> listStockGaps() {
        return Result.success(wiringPlanService.listStockGaps());
    }

    /**
     * 库存缺口按分区汇总：缺口件数与涉及配件种数，未分配分区单独一行；
     * 与缺口列表同一口径，必须声明在 /{id} 之前，避免被当作方案 ID 匹配
     */
    @GetMapping("/stock-gaps/zone-summary")
    public Result<List<StockGapZoneSummaryVO>> listStockGapZoneSummary() {
        return Result.success(wiringPlanService.listStockGapZoneSummary());
    }

    /**
     * 导出缺口分区汇总 CSV：分区小计逐行列出，末尾追加合计行；
     * 与页面“按分区汇总”表格同源，刷新后页面合计、分区小计与导出文件一致。
     * 空汇总也返回 CSV（表头 + 合计 0 行），文件名含中文使用 RFC 5987 filename* 编码
     */
    @GetMapping("/stock-gaps/zone-summary/export")
    public void exportStockGapZoneSummary(HttpServletResponse response) throws IOException {
        List<StockGapZoneSummaryVO> rows = wiringPlanService.listStockGapZoneSummary();

        List<String[]> dataRows = new ArrayList<>();
        int totalAccessoryCount = 0;
        int totalGapQuantity = 0;
        for (StockGapZoneSummaryVO row : rows) {
            int accessoryCount = row.getShortageAccessoryCount() == null ? 0 : row.getShortageAccessoryCount();
            int gapQuantity = row.getGapQuantityTotal() == null ? 0 : row.getGapQuantityTotal();
            totalAccessoryCount += accessoryCount;
            totalGapQuantity += gapQuantity;
            dataRows.add(new String[]{
                    row.getZoneTagName(),
                    String.valueOf(accessoryCount),
                    String.valueOf(gapQuantity)
            });
        }
        // 合计行：与页面汇总表合计行同一口径（分区小计之和）
        dataRows.add(new String[]{"合计", String.valueOf(totalAccessoryCount), String.valueOf(totalGapQuantity)});

        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String chineseFileName = "库存缺口分区汇总_" + datePart + ".csv";
        String encodedFileName = URLEncoder.encode(chineseFileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"stock-gap-zone-summary-" + datePart + ".csv\"; "
                        + "filename*=UTF-8''" + encodedFileName);
        CsvExporter.write(response.getOutputStream(), ZONE_SUMMARY_EXPORT_HEADERS, dataRows);
    }

    /**
     * 按方案核销出库：必须填写领料人与领料说明，校验通过后扣减配件现存量并登记核销记录；
     * 停用方案、已核销方案、含已删除配件或现存量不足时返回明确错误
     */
    @PutMapping("/{id}/writeoff")
    public Result<Void> writeoff(@PathVariable Long id, @Valid @RequestBody WriteoffDTO dto) {
        wiringPlanService.writeoff(id, dto);
        return Result.success();
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody WiringPlanDTO dto) {
        wiringPlanService.add(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody WiringPlanDTO dto) {
        wiringPlanService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wiringPlanService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        wiringPlanService.updateStatus(id, status);
        return Result.success();
    }
}
