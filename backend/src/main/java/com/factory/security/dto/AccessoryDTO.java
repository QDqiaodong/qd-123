package com.factory.security.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccessoryDTO {

    private Long id;

    @NotBlank(message = "配件名称不能为空")
    private String accessoryName;

    @NotBlank(message = "型号不能为空")
    private String model;

    private String material;

    private String scene;

    @DecimalMin(value = "0", message = "规格最小值不能为负数")
    private BigDecimal specMin;

    @DecimalMin(value = "0", message = "规格最大值不能为负数")
    private BigDecimal specMax;

    private String specUnit;

    /** 所属分区允许为空：未分配分区的配件在缺口列表中单独列出 */
    private Long zoneTagId;

    @NotNull(message = "现存量不能为空")
    @Min(value = 0, message = "现存量不能为负数")
    private Integer stockQuantity;

    private String remark;
}
