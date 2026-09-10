package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 布线方案导出行：一行对应一个方案配件明细，方案信息在同一方案的各行重复以保留方案边界
 */
@Data
public class WiringPlanExportRowVO implements Serializable {

    private Long planId;

    /** 方案名称 */
    private String planName;

    /** 适用场景 */
    private String scene;

    /** 启用状态展示文案：启用 / 停用 */
    private String statusText;

    /** 配件名称（方案无配件或配件已删除时为空串） */
    private String accessoryName;

    /** 所属分区展示文案：取分区标签名，无分区时为“未分配分区” */
    private String zoneTagName;

    /** 需求数量（方案无配件行时为空串） */
    private String quantityText;

    /** 规格单位（配件未填写单位时为空串） */
    private String specUnit;
}
