package com.factory.security.vo;

import com.factory.security.entity.WiringPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class WiringPlanVO extends WiringPlan {

    private Integer detailCount;

    private List<WiringPlanDetailVO> details;
}
