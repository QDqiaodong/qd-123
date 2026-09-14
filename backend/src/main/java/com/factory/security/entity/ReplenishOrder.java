package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 补货单：仓管在安全库存台账勾选低于下限的配件生成，单据按分区汇总缺口件数。
 * status=0 待提交（草稿，可调整补货数量、可删除，不占用配件待补标记）；
 * status=1 已提交（档案回填补货单号与待补数量，数量锁定不可再改）；
 * status=2 已作废（清除档案待补标记，单据只读留档）。
 */
@Data
@TableName("replenish_order")
public class ReplenishOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 补货单号（业务编号） */
    private String replenishNo;

    /** 状态：0-待提交，1-已提交，2-已作废 */
    private Integer status;

    /** 明细配件种数 */
    private Integer itemCount;

    /** 补货件数合计（所有明细补货数量求和，随草稿调整实时回写） */
    private Integer totalQuantity;

    /** 作废原因 */
    private String cancelReason;

    private LocalDateTime submitTime;

    private LocalDateTime cancelTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
