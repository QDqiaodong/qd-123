package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.AccessoryDTO;
import com.factory.security.dto.Result;
import com.factory.security.entity.Accessory;
import com.factory.security.service.AccessoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public Result<Void> updateZone(@PathVariable Long id, @RequestParam Long zoneTagId) {
        accessoryService.updateZone(id, zoneTagId);
        return Result.success();
    }
}
