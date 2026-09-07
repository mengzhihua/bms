package com.bms.settlement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账单。status: DRAFT / CONFIRMED / DISPUTED / SETTLED / CANCELLED */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_statement")
public class Statement extends BaseEntity {
    private String statementNo;
    private String direction;
    private String partnerCode;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer feeCount;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal adjustAmount;
    private BigDecimal paidAmount;
    private BigDecimal invoicedAmount;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime confirmedAt;
    private String confirmedBy;
    private String disputeReason;
    private String remark;
}
