package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("accessory")
public class Accessory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accessoryName;

    private String model;

    private String material;

    private String scene;

    private BigDecimal specMin;

    private BigDecimal specMax;

    private String specUnit;

    private Long zoneTagId;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
