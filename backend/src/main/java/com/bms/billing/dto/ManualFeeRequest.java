package com.bms.billing.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ManualFeeRequest {
    @NotBlank
    private String direction;
    @NotBlank
    private String partnerCode;
    @NotBlank
    private String chargeItemCode;
    @NotNull
    private BigDecimal amount;
    private BigDecimal taxRate;
    private LocalDate bizDate;
    private String docNo;
    private String statementNo;
    private String warehouseCode;
    private String bizType;
    private String unit;
    private BigDecimal qty;
    private String remark;
}
