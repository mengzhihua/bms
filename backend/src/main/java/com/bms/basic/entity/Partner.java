package com.bms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 结算对象。type: CUSTOMER 客户(应收) / CARRIER 承运商(应付) / SUPPLIER 供应商(应付) */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_partner")
public class Partner extends BaseEntity {
    private String code;
    private String name;
    private String type;
    private String contact;
    private String phone;
    private String taxNo;
    private String bankAccount;
    private String address;
    private String settleCycle;
    private BigDecimal creditLimit;
    private Integer status;
}
