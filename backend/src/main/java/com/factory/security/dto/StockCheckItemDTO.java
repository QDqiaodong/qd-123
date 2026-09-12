package com.factory.security.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 登记实盘数：仅待确认盘点单可登记，可部分登记、反复覆盖登记；
 * 已删除配件即使被前端带回，服务端也会忽略，不影响其只展示不回写的规则
 */
@Data
public class StockCheckItemDTO {

    @NotEmpty(message = "实盘明细不能为空")
    @Valid
    private List<StockCheckActualDTO> items;

    @Data
    public static class StockCheckActualDTO {

        private Long itemId;

        @jakarta.validation.constraints.NotNull(message = "实盘数量不能为空")
        @jakarta.validation.constraints.Min(value = 0, message = "实盘数量不能为负数")
        private Integer actualQuantity;
    }
}
