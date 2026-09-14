package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.InspectionCreateDTO;
import com.factory.security.dto.InspectionQualifyDTO;
import com.factory.security.dto.InspectionResultDTO;
import com.factory.security.dto.Result;
import com.factory.security.service.InspectionOrderService;
import com.factory.security.vo.InspectionOrderVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 辅材送检单（仓管按已建档配件送检）：送检时必须写明送检批次与实验室名称，缺一不能提交；
 * 新建后单据处于“待回样”，实验室写回结论后变为“已回样”，只有已回样才能标记合格，
 * 未回样不能标合格。列表可按是否已回样筛选。
 */
@RestController
@RequestMapping("/inspection-order")
public class InspectionOrderController {

    @Autowired
    private InspectionOrderService inspectionOrderService;

    @GetMapping("/page")
    public Result<Page<InspectionOrderVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer sampleReturned,
            @RequestParam(required = false) Integer qualified,
            @RequestParam(required = false) String keyword) {
        return Result.success(inspectionOrderService.page(
                pageNum, pageSize, sampleReturned, qualified, keyword));
    }

    /** 新建送检单：必须写明送检批次与实验室名称，新建后待回样；返回新送检单ID */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InspectionCreateDTO dto) {
        return Result.success(inspectionOrderService.create(dto));
    }

    /** 实验室写回结论：仅待回样单可写回，结论非空白，写回后单据变为已回样 */
    @PutMapping("/{id}/result")
    public Result<Void> writeResult(@PathVariable Long id,
                                    @Valid @RequestBody InspectionResultDTO dto) {
        inspectionOrderService.writeResult(id, dto);
        return Result.success();
    }

    /** 标记/取消合格：未回样（未写回结论）不能标合格，后端强制 */
    @PutMapping("/{id}/qualify")
    public Result<Void> qualify(@PathVariable Long id,
                                @Valid @RequestBody InspectionQualifyDTO dto) {
        inspectionOrderService.qualify(id, dto);
        return Result.success();
    }
}
