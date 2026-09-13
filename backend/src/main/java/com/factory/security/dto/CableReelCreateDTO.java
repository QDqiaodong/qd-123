package com.factory.security.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 整盘电源线建档参数：盘号、绑定配件、盘上剩余（整盘）米数。
 * 盘号重复、配件不存在或已删除由 Service 拒绝；建档后初始状态恒为“未开盘”。
 */
@Data
public class CableReelCreateDTO {

    /** 盘号（必填，去空格后非空，同一盘号不能建两次） */
    @NotBlank(message = "请填写盘号")
    @Size(max = 60, message = "盘号长度不能超过60个字符")
    private String reelNo;

    /** 绑定的配件ID（必填，且必须是未删除的配件） */
    @NotNull(message = "请选择绑定的配件")
    private Long accessoryId;

    /** 盘上剩余米数（建档即整盘米数，非负整数；0 米盘也允许建档但开盘后无米可扣） */
    @NotNull(message = "请填写盘上剩余米数")
    @Min(value = 0, message = "盘上剩余米数不能为负数")
    private Integer remainingMeters;
}
