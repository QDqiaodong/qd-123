package com.factory.security.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盘点单列表行/头信息：状态、明细种数、差异种数与确认时间
 */
@Data
public class StockCheckVO {

    private Long id;

    private String checkNo;

    /** 盘点分区标签ID，NULL 表示未分配分区 */
    private Long zoneTagId;

    private String zoneName;

    private Boolean unassignedZone;

    /** 状态：0-待确认，1-已确认 */
    private Integer status;

    private String statusText;

    /** 明细配件种数（含已删除配件） */
    private Integer itemCount;

    /** 已登记实盘数的配件种数（列表展示登记进度） */
    private Integer recordedCount;

    /** 盘盈盘亏配件种数（已删除配件不参与） */
    private Integer diffCount;

    private String confirmRemark;

    private LocalDateTime confirmTime;

    private LocalDateTime createTime;
}
