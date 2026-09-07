package com.bms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 费用项目。category: STORAGE/HANDLING/TRANSPORT/VAS/OTHER; direction: AR/AP/BOTH */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_charge_item")
public class ChargeItem extends BaseEntity {
    private String code;
    private String name;
    private String category;
    private String direction;
    private BigDecimal defaultTaxRate;
    private Integer status;
}
