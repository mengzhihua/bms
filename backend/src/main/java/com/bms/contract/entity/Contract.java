package com.bms.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 合同。direction: AR 应收 / AP 应付; status: DRAFT / ACTIVE / EXPIRED / TERMINATED */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_contract")
public class Contract extends BaseEntity {
    private String contractNo;
    private String name;
    private String direction;
    private String partnerCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String settleCycle;
    private String currency;
    private BigDecimal taxRate;
    private Integer paymentDays;
    private String status;
    private String remark;
}
