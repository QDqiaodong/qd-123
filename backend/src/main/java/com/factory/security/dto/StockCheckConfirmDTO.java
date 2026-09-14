package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 盘点确认参数：必须填写差异说明后才能确认回写；
 * 确认后在同一事务内按实盘数一次性回写配件现存量，盘点单随即只读不可再改
 */
@Data
public class StockCheckConfirmDTO {

    /** 差异说明：确认回写前必填，纯空白不允许确认 */
    @NotBlank(message = "请填写差异说明后再确认盘点回写")
    @Size(max = 500, message = "差异说明长度不能超过500个字符")
    private String confirmRemark;
}
