package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 光纤熔接接头登记：仓管单独建账，按接头编号登记所属分区、盘留米数与是否过 OTDR。
 * otdr_passed=1（已过 OTDR）才允许标记可投运（commissionable=1）；
 * status=0 在档、1 已作废，作废只留档不做物理删除，作废后单据只读。
 * 接头编号全局唯一（uk_splice_no）
 */
@Data
@TableName("fiber_splice_joint")
public class FiberSpliceJoint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接头编号（业务编号，全局唯一） */
    private String spliceNo;

    /** 所属分区标签ID，NULL 表示未分配分区 */
    private Long zoneTagId;

    /** 分区名称（登记/调整时快照，分区标签删除后仍可展示） */
    private String zoneName;

    /** 盘留米数（非负整数） */
    private Integer reserveMeters;

    /** 是否过 OTDR：0-未过，1-已过；未过不能标记可投运 */
    private Integer otdrPassed;

    /** 是否可投运：0-不可投运，1-可投运；仅已过 OTDR 才允许置 1 */
    private Integer commissionable;

    /** 状态：0-在档，1-已作废（作废只留档，不物理删除） */
    private Integer status;

    /** 作废原因 */
    private String voidReason;

    /** 作废时间 */
    private LocalDateTime voidTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
