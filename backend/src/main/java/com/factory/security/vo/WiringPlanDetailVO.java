package com.factory.security.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class WiringPlanDetailVO implements Serializable {

    private Long id;

    private Long planId;

    private Long accessoryId;

    private String accessoryName;

    private String model;

    private String specUnit;

    private Long zoneTagId;

    private String zoneTagName;

    private Integer quantity;

    /** 配件现存量（配件已删除时为 null） */
    private Integer stockQuantity;

    /** 配件是否已删除：已删除配件仍展示但不可核销出库 */
    private Boolean accessoryDeleted;
}
