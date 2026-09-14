package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 实验室回样结论写回参数：结论必填（纯空白不允许回样）。
 * 写回后单据才从“待回样”变为“已回样”，之后才能标记合格
 */
@Data
public class InspectionResultDTO {

    /** 实验室回样结论（必填，纯空格不能回样） */
    @NotBlank(message = "请填写实验室回样结论")
    @Size(max = 1000, message = "回样结论长度不能超过1000个字符")
    private String labConclusion;
}
