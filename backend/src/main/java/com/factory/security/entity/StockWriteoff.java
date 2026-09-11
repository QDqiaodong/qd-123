package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 方案出库核销记录：每个方案至多一条（uk_plan_id），
 * 核销时按方案明细一次性扣减配件现存量；同一方案不可重复核销
 */
@Data
@TableName("stock_writeoff")
public class StockWriteoff implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String planName;

    private String remark;

    private LocalDateTime createTime;
}
