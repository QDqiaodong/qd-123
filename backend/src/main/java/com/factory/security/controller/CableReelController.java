package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.CableReelCreateDTO;
import com.factory.security.dto.CableReelDeductDTO;
import com.factory.security.dto.Result;
import com.factory.security.service.CableReelService;
import com.factory.security.vo.CableReelVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 整盘电源线（线缆盘）档案：按盘号建档（盘号唯一、绑定配件、盘上剩余米数），
 * 建档为“未开盘”，开盘确认后整盘米数一次性计入配件档案现存量，此后才能从该盘扣米；
 * 盘上剩余与配件档案现存量在同一事务内联动，刷新后二者一致
 */
@RestController
@RequestMapping("/cable-reel")
public class CableReelController {

    @Autowired
    private CableReelService cableReelService;

    @GetMapping("/page")
    public Result<Page<CableReelVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long accessoryId) {
        return Result.success(cableReelService.page(pageNum, pageSize, keyword, status, accessoryId));
    }

    /** 盘号建档：取消对话框时前端不发请求，后端亦以盘号唯一索引兜底同一盘号不能建两次 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CableReelCreateDTO dto) {
        return Result.success(cableReelService.create(dto));
    }

    /** 开盘确认：整盘米数一次性入账到配件档案现存量，盘置为已开盘，同一配件同时只开一个盘 */
    @PutMapping("/{id}/open")
    public Result<Void> open(@PathVariable Long id) {
        cableReelService.open(id);
        return Result.success();
    }

    /** 扣米：仅已开盘盘可扣，盘上剩余与配件档案现存量同事务扣减，未开过的盘不能扣 */
    @PutMapping("/{id}/deduct")
    public Result<Void> deduct(@PathVariable Long id,
                               @Valid @RequestBody CableReelDeductDTO dto) {
        cableReelService.deduct(id, dto);
        return Result.success();
    }

    /** 删除：仅未开盘盘可删除，已开盘盘的米数已计入档案需保留 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cableReelService.delete(id);
        return Result.success();
    }
}
