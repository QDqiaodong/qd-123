package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.Result;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.service.WiringPlanService;
import com.factory.security.util.CsvExporter;
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
