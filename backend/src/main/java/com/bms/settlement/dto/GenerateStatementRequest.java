package com.bms.settlement.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class GenerateStatementRequest {
    @NotBlank
    private String direction;
    @NotBlank
    private String partnerCode;
    @NotNull
    private LocalDate periodStart;
    @NotNull
    private LocalDate periodEnd;
    private String remark;
}
