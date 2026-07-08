package com.factory.security.dto;

import jakarta.validation.constraints.DecimalMin;
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

    @NotNull(message = "所属分区不能为空")
    private Long zoneTagId;

    private String remark;
}
