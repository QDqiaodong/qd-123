package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.FiberSpliceCommissionDTO;
import com.factory.security.dto.FiberSpliceJointCreateDTO;
import com.factory.security.dto.FiberSpliceJointUpdateDTO;
import com.factory.security.dto.FiberSpliceVoidDTO;
import com.factory.security.dto.Result;
import com.factory.security.service.FiberSpliceJointService;
import com.factory.security.vo.FiberSpliceJointVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 光纤熔接接头登记（仓管单独建账）：记下接头编号、所属分区、盘留米数、是否过 OTDR。
 * 列表可按所属分区筛选；未过 OTDR 不能标记可投运；作废只置“已作废”留档，
 * 系统不提供物理删除入口。
 */
@RestController
@RequestMapping("/fiber-splice")
public class FiberSpliceJointController {

    @Autowired
    private FiberSpliceJointService fiberSpliceJointService;

    @GetMapping("/page")
    public Result<Page<FiberSpliceJointVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long zoneTagId,
            @RequestParam(required = false) Integer otdrPassed,
            @RequestParam(required = false) Integer commissionable,
            @RequestParam(required = false) Integer status) {
        return Result.success(fiberSpliceJointService.page(pageNum, pageSize, keyword,
                zoneTagId, otdrPassed, commissionable, status));
    }

    /** 登记新接头：接头编号唯一，登记时不可投运，需通过 OTDR 后再单独标记 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FiberSpliceJointCreateDTO dto) {
        return Result.success(fiberSpliceJointService.create(dto));
    }

    /** 编辑在档接头：所属分区、盘留米数、是否过 OTDR、备注；接头编号不可改，已作废只读 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody FiberSpliceJointUpdateDTO dto) {
        fiberSpliceJointService.update(id, dto);
        return Result.success();
    }

    /** 标记/取消可投运：未过 OTDR 不能标记可投运 */
    @PutMapping("/{id}/commission")
    public Result<Void> commission(@PathVariable Long id,
                                   @Valid @RequestBody FiberSpliceCommissionDTO dto) {
        fiberSpliceJointService.commission(id, dto);
        return Result.success();
    }

    /** 作废：只置“已作废”留档，不做物理删除；请求体与作废原因均可空 */
    @PutMapping("/{id}/void")
    public Result<Void> voidJoint(@PathVariable Long id,
                                  @Valid @RequestBody(required = false) FiberSpliceVoidDTO dto) {
        fiberSpliceJointService.voidJoint(id, dto);
        return Result.success();
    }
}
