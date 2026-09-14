package com.factory.security.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.factory.security.dto.ReplenishCancelDTO;
import com.factory.security.dto.ReplenishCreateDTO;
import com.factory.security.dto.ReplenishItemDTO;
import com.factory.security.dto.Result;
import com.factory.security.service.ReplenishOrderService;
import com.factory.security.vo.ReplenishOrderDetailVO;
import com.factory.security.vo.ReplenishOrderVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 补货单：仓管在安全库存台账勾选低于下限的配件生成，单据按分区汇总缺口件数。
 * 待提交草稿可调整补货数量、可删除；提交后把补货单号与待补数量回写配件档案并锁定数量；
 * 已提交单作废后清除档案待补标记，单据只读留档
 */
@RestController
@RequestMapping("/replenish-order")
public class ReplenishOrderController {

    @Autowired
    private ReplenishOrderService replenishOrderService;

    @GetMapping("/page")
    public Result<Page<ReplenishOrderVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(replenishOrderService.page(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<ReplenishOrderDetailVO> getById(@PathVariable Long id) {
        return Result.success(replenishOrderService.getDetailById(id));
    }

    /** 勾选台账低位配件生成补货单（待提交草稿），服务端重新按档案校验缺口与待补占用 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReplenishCreateDTO dto) {
        return Result.success(replenishOrderService.create(dto));
    }

    /** 调整补货数量：仅待提交草稿可改，可部分行、反复覆盖；已提交/已作废单数量锁定 */
    @PutMapping("/{id}/items")
    public Result<Void> updateItems(@PathVariable Long id,
                                    @Valid @RequestBody ReplenishItemDTO dto) {
        replenishOrderService.updateItems(id, dto);
        return Result.success();
    }

    /** 提交补货单：回写配件档案补货单号与待补数量，提交后数量锁定不可再改 */
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        replenishOrderService.submit(id);
        return Result.success();
    }

    /** 作废已提交补货单：清除档案待补标记（补货单号、待补数量），单据只读留档 */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @Valid @RequestBody(required = false) ReplenishCancelDTO dto) {
        replenishOrderService.cancel(id, dto);
        return Result.success();
    }

    /** 删除补货单：仅待提交草稿可删除（已提交单走作废、已作废单留档） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        replenishOrderService.delete(id);
        return Result.success();
    }
}
