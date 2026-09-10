package com.factory.security.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WiringPlanDTO {

    private Long id;

    @NotBlank(message = "方案名称不能为空")
    private String planName;

    private String scene;

    private String description;

    @NotNull(message = "启用状态不能为空")
    private Integer status;

    @Valid
    private List<WiringPlanDetailDTO> details;
}
