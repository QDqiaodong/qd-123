package com.factory.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WiringPlanDetailDTO {

    private Long id;

    @NotNull(message = "配件不能为空")
    private Long accessoryId;

    @NotNull(message = "需求数量不能为空")
    @Min(value = 1, message = "需求数量必须为正整数")
    private Integer quantity;
}
