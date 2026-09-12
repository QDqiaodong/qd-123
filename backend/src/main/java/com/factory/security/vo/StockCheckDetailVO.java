package com.factory.security.vo;

import lombok.Data;

import java.util.List;

/**
 * 盘点单详情：头信息 + 明细行 + 登记/差异汇总（待确认期间实时重算，刷新后与列表一致）
 */
@Data
public class StockCheckDetailVO {

    private StockCheckVO header;

    private List<StockCheckItemVO> items;

    /** 已登记实盘数种数（不含已删除配件） */
    private Integer recordedCount;

    /** 有差异配件种数（已登记且差异非 0） */
    private Integer diffCount;

    /** 盘盈种数（实盘 &gt; 账面） */
    private Integer gainCount;

    /** 盘亏种数（实盘 &lt; 账面） */
    private Integer lossCount;

    /** 差异件数合计（盘盈为正、盘亏为负） */
    private Integer totalDiffQuantity;
}
