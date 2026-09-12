package com.factory.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 方案核销出库参数：核销即领料出库，领料人与领料说明是对账凭证，二者必填；
 * 缺领料人会导致对账时无法对应“是谁领的”，故在后端做强校验，不依赖前端控制
 */
@Data
public class WriteoffDTO {

    @NotBlank(message = "领料人不能为空")
    @Size(max = 100, message = "领料人长度不能超过100个字符")
    private String receiver;

    @NotBlank(message = "领料说明不能为空")
    @Size(max = 500, message = "领料说明长度不能超过500个字符")
    private String remark;
}
