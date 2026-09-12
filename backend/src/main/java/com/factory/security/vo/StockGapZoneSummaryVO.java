package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 库存缺口分区汇总行：一个分区一行，未分配分区单独一行（位于最后）。
 * 由缺口明细列表归组得出，与明细同一口径，保证页面合计、分区小计与导出文件一致。
 */
@Data
public class StockGapZoneSummaryVO implements Serializable {

    /** 分区标签 ID，未分配分区为 null */
    private Long zoneTagId;

    /** 分区名称，未分配分区为“未分配分区” */
    private String zoneTagName;

    /** 是否未分配分区 */
    private Boolean unassignedZone;

    /** 涉及配件种数：该分区内现存量不足（缺口大于 0）的配件种数，已删除配件不参与 */
    private Integer shortageAccessoryCount;

    /** 缺口件数：该分区内各配件缺口数量合计 */
    private Integer gapQuantityTotal;
}
