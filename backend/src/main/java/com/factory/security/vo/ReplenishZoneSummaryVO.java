package com.factory.security.vo;

import lombok.Data;

/**
 * 补货单按分区汇总行：把单内各明细补货数量按生成时快照的分区求和，
 * 供采购按分区领料；未分配分区单独一行且恒在最后
 */
@Data
public class ReplenishZoneSummaryVO {

    private Long zoneTagId;

    private String zoneName;

    private Boolean unassignedZone;

    /** 该分区涉及的配件种数 */
    private Integer accessoryCount;

    /** 该分区缺口件数（补货数量合计） */
    private Integer totalQuantity;
}
