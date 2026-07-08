package com.factory.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("zone_tag")
public class ZoneTag implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tagName;

    private String tagCode;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
