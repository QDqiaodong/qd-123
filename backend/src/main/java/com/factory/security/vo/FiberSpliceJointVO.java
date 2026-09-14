package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 光纤熔接接头登记行 VO：除接头自身字段外回填分区快照名称与分区是否已被删除，
 * 便于按分区筛选、列表直接展示，刷新后与登记数据同源一致
 */
@Data
public class FiberSpliceJointVO implements Serializable {

    private Long id;

    /** 接头编号 */
    private String spliceNo;

    /** 所属分区标签ID */
    private Long zoneTagId;

    /** 所属分区名称（快照，分区标签删除后仍展示） */
    private String zoneName;

    /** 所属分区标签是否已被删除（快照名称继续展示，仅给标记） */
    private Boolean zoneDeleted;

    /** 盘留米数 */
    private Integer reserveMeters;

    /** 是否过 OTDR：0-未过，1-已过 */
    private Integer otdrPassed;

    /** 是否过 OTDR 文案 */
    private String otdrPassedText;

    /** 是否可投运：0-不可投运，1-可投运 */
    private Integer commissionable;

    /** 是否可投运文案 */
    private String commissionableText;

    /** 状态：0-在档，1-已作废 */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    /** 作废原因 */
    private String voidReason;

    /** 作废时间 */
    private LocalDateTime voidTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
