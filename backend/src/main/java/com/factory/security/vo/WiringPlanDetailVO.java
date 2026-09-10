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
}
