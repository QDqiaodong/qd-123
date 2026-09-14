package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分区盘点单：按分区开盘登记实盘数。
 * status=0 待确认（可反复登记实盘数，不动库存）；status=1 已确认（库存已一次性回写，单据只读）。
 * 同一分区同时只允许一张待确认单（数据库 uk_pending_zone 生成列唯一索引兜底）
 */
@Data
@TableName("stock_check")
public class StockCheck implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点单号（业务编号） */
    private String checkNo;

    /** 盘点分区标签ID，NULL 表示未分配分区 */
    private Long zoneTagId;

    /** 分区名称（开盘时快照，分区标签删除后仍可展示） */
    private String zoneName;

    /** 是否未分配分区：0-否，1-是 */
    private Integer unassignedZone;

    /** 状态：0-待确认，1-已确认 */
    private Integer status;

    /** 明细配件种数（含已删除配件） */
    private Integer itemCount;

    /** 盘盈盘亏配件种数（已删除配件不参与） */
    private Integer diffCount;

    /** 差异说明（确认回写前必填，随单据持久化，刷新后仍展示） */
    private String confirmRemark;

    private LocalDateTime confirmTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
