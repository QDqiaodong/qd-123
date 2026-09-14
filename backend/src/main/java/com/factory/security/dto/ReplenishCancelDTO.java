package com.factory.security.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作废补货单参数：作废原因用于留档（可选填）。
 * 作废后清除单据内全部配件档案上的待补标记（补货单号、待补数量），单据本身只读保留
 */
@Data
public class ReplenishCancelDTO {

    @Size(max = 500, message = "作废原因长度不能超过500个字符")
    private String cancelReason;
}
