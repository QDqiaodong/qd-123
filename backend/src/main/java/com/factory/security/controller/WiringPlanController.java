package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.Result;
import com.factory.security.dto.WiringPlanDTO;
import com.factory.security.service.WiringPlanService;
import com.factory.security.vo.WiringPlanVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wiring-plan")
@CrossOrigin
public class WiringPlanController {

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
