package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 辅材送检单：仓管按已建档配件新建送检单，送检时必须写明送检批次与实验室名称。
 * sample_returned=0 待回样（实验室尚未写回结论），sample_returned=1 已回样（结论已写回）；
 * qualified=1 合格只能在已回样后置位，未回样不能标合格（“合格 ⇒ 已回样”恒成立）。
 * 送检单号全局唯一（uk_inspection_no）
 */
@Data
@TableName("inspection_order")
public class InspectionOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 送检单号（业务编号，全局唯一） */
    private String inspectionNo;

    /** 送检配件ID（必须为已建档且未删除的配件） */
    private Long accessoryId;

    /** 配件名称（新建时快照） */
    private String accessoryName;

    /** 型号（新建时快照） */
    private String model;

    /** 规格单位（新建时快照） */
    private String specUnit;

    /** 送检批次（必填，纯空格视为未填） */
    private String batchNo;

    /** 实验室名称（必填，纯空格视为未填） */
    private String labName;

    /** 是否已回样：0-待回样，1-已回样（实验室写回结论）；未回样不能标合格 */
    private Integer sampleReturned;

    /** 是否合格：0-未判定，1-合格；仅已回样且结论写回后才允许置 1 */
    private Integer qualified;

    /** 实验室回样结论（回样时写回，纯空白不允许回样） */
    private String labConclusion;

    /** 回样时间 */
    private LocalDateTime sampleReturnTime;

    /** 判定合格时间 */
    private LocalDateTime qualifiedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
