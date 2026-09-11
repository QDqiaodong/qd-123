package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 配件分区调整请求：目标分区允许为空（设为未分配分区），调整原因必填。
 */
@Data
public class ZoneAdjustDTO {

    /** 目标分区ID，null 表示调整为未分配分区 */
    private Long zoneTagId;

    @NotBlank(message = "调整原因不能为空")
    @Size(max = 500, message = "调整原因不能超过500个字符")
    private String reason;
}
