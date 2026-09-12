package com.factory.security.vo;

import com.factory.security.entity.WiringPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WiringPlanVO extends WiringPlan {

    private Integer detailCount;

    private List<WiringPlanDetailVO> details;

    /** 是否已核销出库：同一方案核销一次后不可重复核销 */
    private Boolean writeoff;

    /** 核销时间（未核销为 null） */
    private LocalDateTime writeoffTime;

    /** 领料人（核销出库时必填，未核销为 null），刷新后与核销记录同源 */
    private String writeoffReceiver;

    /** 领料说明（核销出库时必填，未核销为 null），刷新后与核销记录同源 */
    private String writeoffRemark;

    /** 方案明细是否全部配件库存充足（已核销/停用/无明细时不作不足判断） */
    private Boolean stockSufficient;

    /** 方案是否引用了已删除配件（存在则不可核销） */
    private Boolean hasDeletedAccessory;
}
