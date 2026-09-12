package com.factory.security.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 盘点确认参数：确认后在同一事务内按实盘数一次性回写配件现存量，盘点单随即只读不可再改
 */
@Data
public class StockCheckConfirmDTO {

    @Size(max = 500, message = "确认备注长度不能超过500个字符")
    private String confirmRemark;
}
