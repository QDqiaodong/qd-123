package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.Result;
import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.service.StockCheckService;
import com.factory.security.util.CsvExporter;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckItemVO;
import com.factory.security.vo.StockCheckVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 分区盘点：按分区开盘登记实盘数，待确认期间不改库存；
 * 确认后同一事务一次性按实盘数回写配件现存量，盘点单随即只读不可再改
 */
@RestController
@RequestMapping("/stock-check")
@CrossOrigin(exposedHeaders = "Content-Disposition")
public class StockCheckController {

    /** 差异明细导出列：配件名称、账面数、实盘数、盈亏件数（盘盈为正、盘亏为负） */
    private static final String[] DIFF_EXPORT_HEADERS = {
            "配件名称", "账面数", "实盘数", "盈亏件数"
    };

    @Autowired
    private StockCheckService stockCheckService;

    @GetMapping("/page")
    public Result<Page<StockCheckVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long zoneTagId,
            @RequestParam(required = false) Boolean unassigned) {
        // unassigned=true 表示只看“未分配分区”的盘点单（zoneTagId IS NULL），
        // 与按具体分区筛选互斥；前端用占位值 0 表示未分配分区
        Long effectiveZoneTagId = Boolean.TRUE.equals(unassigned) ? null : zoneTagId;
        return Result.success(
                stockCheckService.page(pageNum, pageSize, status, effectiveZoneTagId,
                        Boolean.TRUE.equals(unassigned)));
    }

    @GetMapping("/{id}")
    public Result<StockCheckDetailVO> getById(@PathVariable Long id) {
        return Result.success(stockCheckService.getDetailById(id));
    }

    /**
     * 导出已确认盘点单的差异明细 CSV（UTF-8 BOM）。
     * 只列盘盈/盘亏配件（已删除配件、账实一致配件不导出），列为
     * 配件名称、账面数、实盘数、盈亏件数（盘盈带 +、盘亏带 -）；
     * 无差异行（含空分区、全部一致）时仅导出表头；有差异行时末尾追加合计行，
     * 合计与抽屉详情同一口径，刷新后与页面差异种数、盈亏件数一致。
     * 待确认单拒绝导出并返回明确原因。文件名含中文，使用 RFC 5987 filename* 编码
     */
    @GetMapping("/{id}/diff-export")
    public void exportDiff(@PathVariable Long id, HttpServletResponse response) throws IOException {
        StockCheckDetailVO detail = stockCheckService.getConfirmedDetailForExport(id);

        List<String[]> dataRows = new ArrayList<>();
        for (StockCheckItemVO item : detail.getItems()) {
            if (Boolean.TRUE.equals(item.getAccessoryDeleted()) || item.getDiffQuantity() == null
                    || item.getDiffQuantity() == 0) {
                // 已删除配件只展示不回写、账实一致项不属于差异明细
                continue;
            }
            dataRows.add(new String[]{
                    item.getAccessoryName(),
                    String.valueOf(item.getBookQuantity()),
                    String.valueOf(item.getActualQuantity()),
                    formatSignedDiff(item.getDiffQuantity())
            });
        }
        // 有差异行才追加合计行：差异种数与盈亏件数直接取详情汇总，
        // 与页面抽屉的“差异种数/差异合计”同源，刷新后保持一致；无差异时文件只有表头
        if (!dataRows.isEmpty()) {
            int diffCount = detail.getDiffCount() == null ? 0 : detail.getDiffCount();
            int totalDiffQuantity = detail.getTotalDiffQuantity() == null ? 0 : detail.getTotalDiffQuantity();
            dataRows.add(new String[]{
                    "合计（差异" + diffCount + "种）", "", "", formatSignedDiff(totalDiffQuantity)
            });
        }

        String checkNo = detail.getHeader() == null ? String.valueOf(id) : detail.getHeader().getCheckNo();
        String asciiFallback = "stock-check-diff-" + checkNo + ".csv";
        String chineseFileName = "盘点差异明细_" + checkNo + ".csv";
        String encodedFileName = URLEncoder.encode(chineseFileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encodedFileName);
        CsvExporter.write(response.getOutputStream(), DIFF_EXPORT_HEADERS, dataRows);
    }

    /** 盈亏件数：0 显示 0，正数带 +（盘盈），负数自带 -（盘亏） */
    private String formatSignedDiff(int diffQuantity) {
        return diffQuantity > 0 ? "+" + diffQuantity : String.valueOf(diffQuantity);
    }

    /** 按分区开盘；zoneTagId 不传（null）表示给未分配分区开盘，空分区也允许开盘 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody StockCheckCreateDTO dto) {
        return Result.success(stockCheckService.create(dto));
    }

    /** 登记实盘数：仅待确认单可改，可部分登记、反复覆盖登记，不修改库存 */
    @PutMapping("/{id}/items")
    public Result<Void> recordItems(@PathVariable Long id,
                                    @Valid @RequestBody StockCheckItemDTO dto) {
        stockCheckService.recordItems(id, dto);
        return Result.success();
    }

    /** 确认盘点：一次性回写现存量并锁单，已删除配件只展示不回写 */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id,
                                @Valid @RequestBody(required = false) StockCheckConfirmDTO dto) {
        stockCheckService.confirm(id, dto);
        return Result.success();
    }

    /** 删除盘点单：仅待确认单可删除，已确认单作为库存回写凭证保留 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stockCheckService.delete(id);
        return Result.success();
    }
}
