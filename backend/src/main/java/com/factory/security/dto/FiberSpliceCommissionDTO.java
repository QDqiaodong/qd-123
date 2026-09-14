package com.factory.security.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 光纤熔接接头“可投运”标记参数。
 * commissionable=1 时 Service 强制要求接头已过 OTDR，未过 OTDR 不能标记可投运。
 */
@Data
public class FiberSpliceCommissionDTO {

    /** 是否可投运：1-标记可投运，0-取消可投运 */
    @NotNull(message = "可投运标记不能为空")
    private Integer commissionable;
}
