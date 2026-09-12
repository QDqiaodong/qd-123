package com.factory.security.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分区盘点开盘参数：zoneTagId 为空表示给“未分配分区”开盘；
 * 分区标签不存在、或同分区已存在待确认盘点单时由 Service 拒绝
 */
@Data
public class StockCheckCreateDTO {

    /** 盘点分区标签ID，NULL 表示未分配分区（空分区、未分配分区均允许开盘） */
    private Long zoneTagId;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;
}
