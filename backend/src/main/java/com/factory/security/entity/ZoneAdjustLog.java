package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配件分区调整流水：每次调区记录原分区、目标分区、调整原因与时间。
 * 配件档案当前所属分区必须与最近一条流水的目标分区一致。
 */
@Data
@TableName("zone_adjust_log")
public class ZoneAdjustLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accessoryId;

    /** 配件名称（调整时快照） */
    private String accessoryName;

    /** 原分区ID，null 表示原未分配分区 */
    private Long fromZoneTagId;

    /** 原分区名称（调整时快照） */
    private String fromZoneName;

    /** 目标分区ID，null 表示调整为未分配分区 */
    private Long toZoneTagId;

    /** 目标分区名称（调整时快照） */
    private String toZoneName;

    /** 调整原因（必填） */
    private String reason;

    private LocalDateTime createTime;
}
