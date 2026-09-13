package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 线缆盘档案行/详情 VO：除盘自身字段外回填绑定配件的实时信息，
 * 便于刷新后直接核对“盘上剩余米数”与“配件档案里的米数（现存量）”是否一致
 */
@Data
public class CableReelVO implements Serializable {

    private Long id;

    /** 盘号 */
    private String reelNo;

    private Long accessoryId;

    /** 配件名称（建档快照） */
    private String accessoryName;

    /** 型号（建档快照） */
    private String model;

    /** 规格单位（建档快照，通常为 m） */
    private String specUnit;

    /** 盘上剩余米数 */
    private Integer remainingMeters;

    /** 状态：0-未开盘，1-已开盘 */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    private LocalDateTime openTime;

    private LocalDateTime createTime;

    /** 配件档案当前现存量（米），刷新后用于与盘上剩余核对 */
    private Integer accessoryStockQuantity;

    /** 绑定配件是否已删除 */
    private Boolean accessoryDeleted;

    /**
     * 已开盘且配件仍正常时，盘上剩余与配件档案现存量是否一致。
     * 未开盘的盘尚未把米数落到档案，不参与一致性判定，返回 null
     */
    private Boolean stockMatched;
}
