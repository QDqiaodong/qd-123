package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.dto.Result;
import com.factory.security.entity.Accessory;
import com.factory.security.service.AccessoryService;
import com.factory.security.vo.SafetyStockVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accessory")
@CrossOrigin
public class AccessoryController {

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
     * 必须声明在 /{id} 之前，避免 safety-stock 被当作配件 ID 匹配；
     * 数据实时计算，响应统一 no-store，改下限或现存量后刷新即与档案一致。
     */
    @GetMapping("/safety-stock")
    public Result<List<SafetyStockVO>> safetyStockShortages() {
        return Result.success(accessoryService.listSafetyStockShortages());
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
