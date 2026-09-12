package com.factory.security.vo;

import lombok.Data;

/**
 * 盘点单明细行：账面现存量为开盘时快照，差异 = 实盘 - 账面；
 * 已删除配件只展示（accessoryDeleted=true），不可登记实盘数、确认时不回写库存
 */
@Data
public class StockCheckItemVO {

    private Long id;

    private Long accessoryId;

    private String accessoryName;

    private String model;

    private String specUnit;

    /** 账面现存量（开盘时快照） */
    private Integer bookQuantity;

    /** 实盘数量，NULL 表示尚未登记 */
    private Integer actualQuantity;

    /** 是否已登记实盘数 */
    private Boolean recorded;

    /** 差异数量 = 实盘 - 账面，未登记或已删除配件为 NULL */
    private Integer diffQuantity;

    /** 差异类型：gain-盘盈，loss-盘亏，even-一致，unrecorded-未登记，deleted-已删除 */
    private String diffType;

    private Boolean accessoryDeleted;
}
