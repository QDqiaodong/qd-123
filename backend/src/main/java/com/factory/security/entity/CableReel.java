package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 整盘电源线（线缆盘）档案：按盘号建档，绑定一个按米计的线缆配件并登记盘上剩余米数。
 * status=0 未开盘（仅建档，盘上米数不进配件档案、不能扣米）；
 * status=1 已开盘（开盘确认后整盘米数一次性落到配件档案现存量，之后才能从盘上扣米）。
 * 盘号全局唯一（uk_reel_no）；同一配件同时只允许一个已开盘（uk_open_accessory 生成列唯一索引兜底）
 */
@Data
@TableName("cable_reel")
public class CableReel implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘号（业务编号，同一盘号不能建两次） */
    private String reelNo;

    /** 绑定的配件ID */
    private Long accessoryId;

    /** 配件名称（建档时快照） */
    private String accessoryName;

    /** 型号（建档时快照） */
    private String model;

    /** 规格单位（建档时快照，通常为 m） */
    private String specUnit;

    /** 盘上剩余米数（非负整数） */
    private Integer remainingMeters;

    /** 状态：0-未开盘，1-已开盘 */
    private Integer status;

    /** 开盘确认时间 */
    private LocalDateTime openTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
