package com.bms.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 费用明细。source: AUTO/MANUAL/ADJUST; status: NEW 未对账 / STATEMENTED 已对账 / SETTLED 已结算 / CANCELLED 作废 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_fee")
public class Fee extends BaseEntity {
    private String feeNo;
    private String direction;
    private String partnerCode;
    private String contractNo;
    private Long ruleId;
    private String docNo;
    private String bizType;
    private String warehouseCode;
    private String chargeItemCode;
    private LocalDate bizDate;
    private String unit;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String source;
    private String status;
    private String statementNo;
    private String calcDetail;
    private String remark;
    private String createdBy;
}
