package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 方案出库核销记录：每个方案至多一条（uk_plan_id），
 * 核销时按方案明细一次性扣减配件现存量；同一方案不可重复核销。
 * 领料人、领料说明为必填：核销出库即领料出库，是对账时“谁领的、领去做什么”的凭证
 */
@Data
@TableName("stock_writeoff")
public class StockWriteoff implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String planName;

    /** 领料人（核销时必填） */
    private String receiver;

    /** 领料说明（核销时必填：领用用途等说明） */
    private String remark;

    private LocalDateTime createTime;
}
