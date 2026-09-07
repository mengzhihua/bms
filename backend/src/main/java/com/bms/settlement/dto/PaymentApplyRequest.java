package com.bms.settlement.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Data
public class PaymentApplyRequest {
    @NotBlank
    private String paymentNo;
    @NotBlank
    private String statementNo;
    private BigDecimal amount;
}
