package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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

    /** 现存量（库存数量），出库核销时按方案需求一次性扣减 */
    private Integer stockQuantity;

    /** 删除标记：0-正常，1-已删除。已删除配件在方案明细中保留展示但不可核销出库 */
    @TableLogic
    private Integer deleted;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
