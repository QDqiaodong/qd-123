package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.Result;
import com.factory.security.dto.StockCheckConfirmDTO;
import com.factory.security.dto.StockCheckCreateDTO;
import com.factory.security.dto.StockCheckItemDTO;
import com.factory.security.service.StockCheckService;
import com.factory.security.vo.StockCheckDetailVO;
import com.factory.security.vo.StockCheckVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 分区盘点：按分区开盘登记实盘数，待确认期间不改库存；
 * 确认后同一事务一次性按实盘数回写配件现存量，盘点单随即只读不可再改
 */
@RestController
@RequestMapping("/stock-check")
@CrossOrigin
public class StockCheckController {

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
