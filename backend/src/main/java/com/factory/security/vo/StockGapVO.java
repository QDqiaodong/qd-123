package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存缺口行：一个配件一行，需求合计只统计已启用且未核销方案。
 * 未分配分区单独成组（前端按 zoneTagId 归组，排序保证其位于最后）；
 * 被方案引用的已删除配件仍列示，但不参与缺口计算、不可核销。
 */
@Data
public class StockGapVO implements Serializable {

    private Long accessoryId;

    private String accessoryName;

    private String model;

    private String specUnit;

    private Long zoneTagId;

    private String zoneTagName;

    /** 现存量（已删除配件为其删除前的库存快照） */
    private Integer stockQuantity;

    /** 已启用且未核销方案的需求合计 */
    private Integer requiredQuantity;

    /** 缺口数量：需求合计 - 现存量，不足时大于 0 */
    private Integer gapQuantity;

    /** 现存量是否不足（缺口大于 0） */
    private Boolean shortage;

    /** 是否未分配分区 */
    private Boolean unassignedZone;

    /** 配件是否已删除：仍显示但不可核销 */
    private Boolean accessoryDeleted;

    private LocalDateTime createTime;
}
