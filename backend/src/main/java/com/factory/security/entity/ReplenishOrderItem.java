package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 补货单明细：生成时从安全库存台账带入勾选的低位配件，
 * 快照配件名称、型号、单位、生成时分区与缺口（下限 - 现存量），补货数量默认等于缺口。
 * 待提交草稿期间可调整补货数量；提交后数量锁定。单据按分区汇总各明细补货数量
 */
@Data
@TableName("replenish_order_item")
public class ReplenishOrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long replenishId;

    private Long accessoryId;

    /** 配件名称（生成时快照） */
    private String accessoryName;

    /** 型号（生成时快照） */
    private String model;

    /** 规格单位（生成时快照） */
    private String specUnit;

    /** 分区标签ID，NULL 表示未分配分区（生成时快照） */
    private Long zoneTagId;

    /** 分区名称（生成时快照，分区标签删除后仍可展示） */
    private String zoneName;

    /** 是否未分配分区：0-否，1-是 */
    private Integer unassignedZone;

    /** 生成时现存量快照 */
    private Integer stockQuantity;

    /** 生成时安全库存下限快照 */
    private Integer safetyStock;

    /** 生成时缺口（下限 - 现存量），恒大于 0 */
    private Integer gapQuantity;

    /** 补货数量：默认等于缺口，草稿期间可调整，提交后锁定 */
    private Integer replenishQuantity;

    /** 配件是否已删除：0-正常，1-已删除（仍随单展示，提交时拒绝） */
    private Integer accessoryDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
