package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 辅材送检单新建参数：按已建档配件送检，送检批次与实验室名称必须填完整，
 * 任一为纯空白都不能提交（Bean Validation + Service 双层强制）
 */
@Data
public class InspectionCreateDTO {

    /** 送检配件ID（必填，且必须是已建档、未删除的配件） */
    @NotNull(message = "请选择送检配件")
    private Long accessoryId;

    /** 送检批次（必填，纯空格视为未填） */
    @NotBlank(message = "请填写送检批次")
    @Size(max = 100, message = "送检批次长度不能超过100个字符")
    private String batchNo;

    /** 实验室名称（必填，纯空格视为未填） */
    @NotBlank(message = "请填写实验室名称")
    @Size(max = 200, message = "实验室名称长度不能超过200个字符")
    private String labName;
}
