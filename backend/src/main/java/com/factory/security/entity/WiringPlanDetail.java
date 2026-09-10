package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("wiring_plan_detail")
public class WiringPlanDetail implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long accessoryId;

    private Integer quantity;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
