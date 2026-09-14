package com.factory.security.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 生成补货单参数：仓管在安全库存台账勾选若干低于下限的配件。
 * 服务端重新按配件档案校验（未删除、已设下限、现存确实低于下限、未被其他有效补货单占用），
 * 不信任前端传入的缺口/现存量；补货数量默认取实时缺口，也可由前端在汇总界面调整后带回
 */
@Data
public class ReplenishCreateDTO {

    @NotEmpty(message = "请至少勾选一个配件再生成补货单")
    @Valid
    private List<ReplenishItemCreateDTO> items;

    @Data
    public static class ReplenishItemCreateDTO {

        private Long accessoryId;

        /**
         * 补货数量：可空，为空时服务端按实时缺口（下限 - 现存量）补齐；
         * 非空时必须为正整数，且不得超过当前缺口（缺口已含补足下限所需全部件数）
         */
        @jakarta.validation.constraints.Positive(message = "补货数量必须为正整数")
        private Integer replenishQuantity;
    }
}
