package com.factory.security.vo;

import lombok.Data;

import java.util.List;

/**
 * 补货单详情：头信息 + 明细行 + 按分区汇总缺口件数。
 * 分区汇总与明细同源（同一组明细按分区分组求和），刷新后分区小计与合计一致
 */
@Data
public class ReplenishOrderDetailVO {

    private ReplenishOrderVO header;

    private List<ReplenishOrderItemVO> items;

    /** 按分区汇总缺口件数（分区排序号升序，未分配分区殿后） */
    private List<ReplenishZoneSummaryVO> zoneSummaries;

    /** 已被其他已提交补货单占用的配件种数（为 0 才允许提交） */
    private Integer conflictCount;
}
