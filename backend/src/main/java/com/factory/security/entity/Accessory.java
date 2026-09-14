package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("accessory")
public class Accessory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accessoryName;

    private String model;

    private String material;

    private String scene;

    private BigDecimal specMin;

    private BigDecimal specMax;

    private String specUnit;

    private Long zoneTagId;

    /** 现存量（库存数量），出库核销时按方案需求一次性扣减 */
    private Integer stockQuantity;

    /** 安全库存下限：null 表示未设下限（不进安全库存台账）；非空且现存量低于该值即列入台账 */
    private Integer safetyStock;

    /**
     * 待补补货单ID：非空表示该配件已被一张已提交补货单占用（待补中）。
     * 提交补货单时回写，作废或后续到货处理时清除；同一配件同时只允许挂在一张有效补货单上。
     * 不使用外键约束，作废单据保留留档时该字段已被清空
     */
    private Long replenishOrderId;

    /** 待补补货单号（提交时快照，档案列表直接展示，避免关联查询） */
    private String replenishOrderNo;

    /** 待补数量（提交补货单时快照的补货数量） */
    private Integer replenishPendingQuantity;

    /** 删除标记：0-正常，1-已删除。已删除配件在方案明细中保留展示但不可核销出库 */
    @TableLogic
    private Integer deleted;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
