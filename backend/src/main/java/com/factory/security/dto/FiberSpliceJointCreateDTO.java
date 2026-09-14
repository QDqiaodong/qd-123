package com.factory.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 光纤熔接接头登记参数：接头编号、所属分区、盘留米数、是否过 OTDR。
 * 新建时不可投运，必须在通过 OTDR 后单独“标记可投运”。
 */
@Data
public class FiberSpliceJointCreateDTO {

    /** 接头编号（必填，去空格后非空，全局唯一） */
    @NotBlank(message = "请填写接头编号")
    @Size(max = 60, message = "接头编号长度不能超过60个字符")
    private String spliceNo;

    /** 所属分区标签ID（必填，且必须是存在的分区） */
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
