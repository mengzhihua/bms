package com.bms.settlement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_invoice")
public class Invoice extends BaseEntity {
    private String invoiceNo;
    private String invoiceCode;
    private String direction;
    private String partnerCode;
    private String statementNo;
    private String invoiceType;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private LocalDate invoiceDate;
    private String status;
    private String remark;
}
