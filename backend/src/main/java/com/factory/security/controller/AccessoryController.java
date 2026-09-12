package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.dto.Result;
import com.factory.security.entity.Accessory;
import com.factory.security.service.AccessoryService;
import com.factory.security.util.CsvExporter;
import com.factory.security.vo.SafetyStockVO;
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
@RequestMapping("/accessory")
@CrossOrigin(exposedHeaders = "Content-Disposition")
public class AccessoryController {

    /** 安全库存台账导出列：名称、分区、现存量、下限、缺口、紧急，与页面台账列一一对应 */
    private static final String[] SAFETY_STOCK_EXPORT_HEADERS = {
            "名称", "分区", "现存量", "下限", "缺口", "紧急"
    };

    @Autowired
    private AccessoryService accessoryService;

    @GetMapping("/page")
    public Result<Page<Accessory>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long zoneTagId) {
        return Result.success(accessoryService.page(pageNum, pageSize, keyword, zoneTagId));
    }

    /**
     * 安全库存台账：仅列设了安全库存下限且现存量低于下限的正常配件（含未分配分区）。
     * 支持按库房分区领料筛选：传 zoneTagId 只看该分区；unassignedZone=true 只看未分配分区；
     * 都不传返回全集。必须声明在 /{id} 之前，避免 safety-stock 被当作配件 ID 匹配；
     * 数据实时计算，响应统一 no-store，改下限或现存量后刷新即与档案一致。
     */
    @GetMapping("/safety-stock")
    public Result<List<SafetyStockVO>> safetyStockShortages(
            @RequestParam(required = false) Long zoneTagId,
            @RequestParam(required = false, defaultValue = "false") boolean unassignedZone) {
        return Result.success(accessoryService.listSafetyStockShortages(zoneTagId, unassignedZone));
    }

    /**
     * 导出当前分区筛选下的台账 CSV：列为名称、分区、现存、下限、缺口、紧急。
     * 与页面台账共用同一 service 口径（同一套筛选参数、同一套排序与紧急标记），
     * 换分区或改下限后重新导出，导出行数、顺序与缺口逐行与页面一致；
     * 未分配分区在文件中写作“未分配分区”。
     * 空结果也返回仅含表头的 CSV（含 UTF-8 BOM，Excel 打开中文不乱码），
     * 文件名含中文使用 RFC 5987 filename* 编码并提供 ASCII 兜底名。
     */
    @GetMapping("/safety-stock/export")
    public void exportSafetyStockShortages(
            @RequestParam(required = false) Long zoneTagId,
            @RequestParam(required = false, defaultValue = "false") boolean unassignedZone,
            HttpServletResponse response) throws IOException {
        List<SafetyStockVO> rows = accessoryService.listSafetyStockShortages(zoneTagId, unassignedZone);

        List<String[]> dataRows = new ArrayList<>();
        for (SafetyStockVO row : rows) {
            String zoneName = Boolean.TRUE.equals(row.getUnassignedZone())
                    ? "未分配分区" : row.getZoneTagName();
            dataRows.add(new String[]{
                    row.getAccessoryName(),
                    zoneName,
                    String.valueOf(row.getStockQuantity()),
                    String.valueOf(row.getSafetyStock()),
                    String.valueOf(row.getGapQuantity()),
                    Boolean.TRUE.equals(row.getUrgent()) ? "紧急" : ""
            });
        }

        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String chineseFileName = "安全库存台账_" + datePart + ".csv";
        String encodedFileName = URLEncoder.encode(chineseFileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"safety-stock-" + datePart + ".csv\"; "
                        + "filename*=UTF-8''" + encodedFileName);
        CsvExporter.write(response.getOutputStream(), SAFETY_STOCK_EXPORT_HEADERS, dataRows);
    }

    @GetMapping("/{id}")
    public Result<Accessory> getById(@PathVariable Long id) {
        return Result.success(accessoryService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody AccessoryDTO dto) {
        accessoryService.add(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody AccessoryDTO dto) {
        accessoryService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accessoryService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/zone")
    public Result<Void> updateZone(@PathVariable Long id,
                                   @RequestParam(required = false) Long zoneTagId) {
        accessoryService.updateZone(id, zoneTagId);
        return Result.success();
    }
}
