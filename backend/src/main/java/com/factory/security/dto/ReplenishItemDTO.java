package com.factory.security.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 调整补货数量：仅待提交补货单可改，可只提交部分行、反复覆盖；
 * 提交后单据锁定，本接口直接拒绝。每行数量必须为正整数
 */
@Data
public class ReplenishItemDTO {

    @NotEmpty(message = "补货明细不能为空")
    @Valid
    private List<ReplenishQuantityDTO> items;

    @Data
    public static class ReplenishQuantityDTO {

        private Long itemId;

        @jakarta.validation.constraints.NotNull(message = "补货数量不能为空")
        @jakarta.validation.constraints.Positive(message = "补货数量必须为正整数")
        private Integer replenishQuantity;
    }
}
