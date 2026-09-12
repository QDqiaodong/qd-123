package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全库存台账行：一个低于安全库存下限的正常配件一行。
 * 口径：仅统计未删除、已设安全库存下限（非 null）且现存量 &lt; 下限的配件；
 * 未设下限、现存量不低于下限、已删除的配件均不进台账。
 * 未分配分区单独标识（unassignedZone=true），在台账中也不能漏掉，由前端成组展示。
 */
@Data
public class SafetyStockVO implements Serializable {

    private Long accessoryId;

    private String accessoryName;

    private String model;

    private String specUnit;

    private Long zoneTagId;

    private String zoneTagName;

    /** 现存量 */
    private Integer stockQuantity;

    /** 安全库存下限（必非空，未设下限不进台账） */
    private Integer safetyStock;

    /** 缺口数量：安全库存下限 - 现存量，恒大于 0 */
    private Integer gapQuantity;

    /** 是否未分配分区：未分配分区的低位配件也必须列入台账 */
    private Boolean unassignedZone;

    private LocalDateTime createTime;
}
