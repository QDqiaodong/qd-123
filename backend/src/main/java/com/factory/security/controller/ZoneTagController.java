package com.factory.security.controller;

import com.factory.security.dto.Result;
import com.factory.security.dto.ZoneTagDTO;
import com.factory.security.entity.ZoneTag;
import com.factory.security.service.ZoneTagService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/zone-tag")
@CrossOrigin
public class ZoneTagController {

    @Autowired
    private ZoneTagService zoneTagService;

    @GetMapping("/list")
    public Result<List<ZoneTag>> list() {
        return Result.success(zoneTagService.listAll());
    }

    @GetMapping("/{id}")
    public Result<ZoneTag> getById(@PathVariable Long id) {
        return Result.success(zoneTagService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody ZoneTagDTO dto) {
        zoneTagService.add(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody ZoneTagDTO dto) {
        zoneTagService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        zoneTagService.delete(id);
        return Result.success();
    }
}
