package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ZoneTagDTO {

    private Long id;

    @NotBlank(message = "分区标签名称不能为空")
    private String tagName;

    @NotBlank(message = "分区标签编码不能为空")
    private String tagCode;

    private Integer sortOrder;

    private String remark;
}
