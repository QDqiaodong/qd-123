package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全库存台账行：一个低于安全库存下限的正常配件一行。
 * 口径：仅统计未删除、已设安全库存下限（非 null）且现存量 &lt; 下限的配件；
 * 未设下限、现存量不低于下限、已删除的配件均不进台账。
 * 未分配分区单独标识（unassignedZone=true），在台账中也不能漏掉，由前端成组展示。
 * 排序供采购按分区领料：先按分区排序号（未分配分区殿后），同一分区内缺口从大到小，
 * 缺口越大越靠前；缺口达到下限一半及以上（2*缺口 &gt;= 下限）标为紧急。
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

    /**
     * 是否紧急：缺口达到安全库存下限的一半及以上（2*缺口 &gt;= 下限）。
     * 改下限或现存量后随缺口实时重算，不使用固化标记，避免紧急状态与新缺口脱节。
     */
    private Boolean urgent;

    /** 是否未分配分区：未分配分区的低位配件也必须列入台账 */
    private Boolean unassignedZone;

    /**
     * 是否已在补货单中待补：档案待补标记非空即 true。
     * 台账据此禁用勾选并展示补货单号，避免同一配件被两张有效补货单重复占用
     */
    private Boolean replenishPending;

    /** 占用该配件的已提交补货单号（待补中直接展示，可与补货单列表对账） */
    private String replenishOrderNo;

    private LocalDateTime createTime;
}
