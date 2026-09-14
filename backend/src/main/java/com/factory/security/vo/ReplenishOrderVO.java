package com.factory.security.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补货单列表行/头信息：状态、明细种数、补货件数合计与提交/作废时间
 */
@Data
public class ReplenishOrderVO {

    private Long id;

    private String replenishNo;

    /** 状态：0-待提交，1-已提交，2-已作废 */
    private Integer status;

    private String statusText;

    /** 明细配件种数 */
    private Integer itemCount;

    /** 补货件数合计（按分区汇总缺口件数的总和） */
    private Integer totalQuantity;

    /** 涉及分区种数（含未分配分区） */
    private Integer zoneCount;

    private String cancelReason;

    private LocalDateTime submitTime;

    private LocalDateTime cancelTime;

    private LocalDateTime createTime;
}
