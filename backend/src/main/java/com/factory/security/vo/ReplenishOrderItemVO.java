package com.factory.security.vo;

import lombok.Data;

/**
 * 补货单明细行：生成时快照配件与分区信息及实时缺口，补货数量默认等于缺口。
 * 已删除配件仍随单展示（accessoryDeleted=true），提交时整单拒绝；
 * pendingConflict=true 表示该配件已被另一张已提交补货单占用，需作废冲突单或删行后重提
 */
@Data
public class ReplenishOrderItemVO {

    private Long id;

    private Long accessoryId;

    private String accessoryName;

    private String model;

    private String specUnit;

    private Long zoneTagId;

    private String zoneName;

    private Boolean unassignedZone;

    /** 生成时现存量快照 */
    private Integer stockQuantity;

    /** 生成时安全库存下限快照 */
    private Integer safetyStock;

    /** 生成时缺口（下限 - 现存量） */
    private Integer gapQuantity;

    /** 补货数量（草稿可改，提交后锁定） */
    private Integer replenishQuantity;

    private Boolean accessoryDeleted;

    /** 该配件当前是否已被其他已提交补货单占用（档案待补标记指向别的单） */
    private Boolean pendingConflict;
}
