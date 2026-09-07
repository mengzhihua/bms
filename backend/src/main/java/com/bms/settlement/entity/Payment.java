package com.bms.settlement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 收付款。direction: AR 收款 / AP 付款 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_payment")
public class Payment extends BaseEntity {
    private String paymentNo;
    private String direction;
    private String partnerCode;
    private BigDecimal amount;
    private BigDecimal appliedAmount;
    private LocalDate payDate;
    private String method;
    private String bankRef;
    private String remark;
}
