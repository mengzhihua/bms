package com.bms.basic.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_warehouse")
public class Warehouse extends BaseEntity {
    private String code;
    private String name;
    private String city;
    private String address;
    private Integer status;
}
