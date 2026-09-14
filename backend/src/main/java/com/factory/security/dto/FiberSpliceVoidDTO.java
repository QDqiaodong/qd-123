package com.factory.security.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 光纤熔接接头作废参数。作废只把状态置为“已作废”留档，不做物理删除；
 * 作废原因可填（最长 500 字）。已作废接头只读，不可重复作废。
 */
@Data
public class FiberSpliceVoidDTO {

    /** 作废原因（可空） */
    @Size(max = 500, message = "作废原因长度不能超过500个字符")
    private String reason;
}
