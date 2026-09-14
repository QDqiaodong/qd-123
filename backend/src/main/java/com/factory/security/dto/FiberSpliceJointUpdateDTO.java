package com.factory.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 光纤熔接接头编辑参数：所属分区、盘留米数、是否过 OTDR、备注。
 * 接头编号一经登记不可改；已作废接头只读。已标记可投运的接头不能把 OTDR 改成未过
 * （需先取消可投运标记），保证“可投运 ⇒ 已过 OTDR”恒成立。
 */
@Data
public class FiberSpliceJointUpdateDTO {

    /** 所属分区标签ID（必填） */
    @NotNull(message = "请选择所属分区")
    private Long zoneTagId;

    /** 盘留米数（非负整数） */
    @NotNull(message = "请填写盘留米数")
    @Min(value = 0, message = "盘留米数不能为负数")
    private Integer reserveMeters;

    /** 是否过 OTDR：0-未过，1-已过（必填） */
    @NotNull(message = "请选择是否过 OTDR")
    private Integer otdrPassed;

    /** 备注（可空） */
    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
