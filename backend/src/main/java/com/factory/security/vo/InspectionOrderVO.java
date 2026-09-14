package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 辅材送检单行 VO：携带配件快照、送检批次、实验室、回样状态/结论与合格标记，
 * 列表直接展示；回样结论随单持久化，刷新后仍在
 */
@Data
public class InspectionOrderVO implements Serializable {

    private Long id;

    /** 送检单号 */
    private String inspectionNo;

    /** 送检配件ID */
    private Long accessoryId;

    /** 配件名称（快照） */
    private String accessoryName;

    /** 型号（快照） */
    private String model;

    /** 规格单位（快照） */
    private String specUnit;

    /** 送检批次 */
    private String batchNo;

    /** 实验室名称 */
    private String labName;

    /** 是否已回样：0-待回样，1-已回样 */
    private Integer sampleReturned;

    /** 回样状态文案 */
    private String sampleReturnedText;

    /** 是否合格：0-未判定，1-合格 */
    private Integer qualified;

    /** 合格状态文案 */
    private String qualifiedText;

    /** 实验室回样结论 */
    private String labConclusion;

    /** 回样时间 */
    private LocalDateTime sampleReturnTime;

    /** 判定合格时间 */
    private LocalDateTime qualifiedTime;

    private LocalDateTime createTime;
}
