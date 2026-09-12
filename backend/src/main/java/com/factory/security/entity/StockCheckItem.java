package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分区盘点单明细：开盘时把分区内配件（含已软删除配件）全部带入并快照账面现存量。
 * 实盘数可反复登记；已删除配件只展示，确认时不回写库存
 */
@Data
@TableName("stock_check_item")
public class StockCheckItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long checkId;

    private Long accessoryId;

    /** 配件名称（开盘时快照） */
    private String accessoryName;

    /** 型号（开盘时快照） */
    private String model;

    /** 规格单位（开盘时快照） */
    private String specUnit;

    /** 账面现存量（开盘时快照） */
    private Integer bookQuantity;

    /** 实盘数量，NULL 表示尚未登记 */
    private Integer actualQuantity;

    /** 配件是否已删除：0-正常，1-已删除（只展示不回写） */
    private Integer accessoryDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
