package com.factory.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 已开盘线缆盘扣米参数：从盘上裁用多少米。
 * 只能对“已开盘”的盘扣米；扣米在同一事务内同步扣减盘上剩余与配件档案现存量，
 * 米数必须为正、且不超过盘上剩余（配件档案米数同源，一并以行级条件兜底，不会扣成负数）。
 */
@Data
public class CableReelDeductDTO {

    /** 本次扣减米数（正整数） */
    @NotNull(message = "请填写扣减米数")
    @Min(value = 1, message = "扣减米数必须为正整数")
    private Integer meters;
}
