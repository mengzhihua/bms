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
@TableName("bms_payment_apply")
public class PaymentApply extends BaseEntity {
    private String paymentNo;
    private String statementNo;
    private BigDecimal amount;
    private String operator;
}
