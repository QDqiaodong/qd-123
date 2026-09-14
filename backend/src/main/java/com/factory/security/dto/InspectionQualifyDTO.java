package com.factory.security.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 标记合格参数：qualified=1 标记合格，0 取消合格标记。
 * 只有已回样（实验室已写回结论）的送检单才允许标记合格
 */
@Data
public class InspectionQualifyDTO {

    /** 是否合格：1-合格，0-取消合格标记（必填） */
    @NotNull(message = "合格标记取值非法")
    private Integer qualified;
}
