package com.bms.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_rate_tier")
public class RateTier extends BaseEntity {
    private Long ruleId;
    private BigDecimal fromQty;
    private BigDecimal toQty;
    private BigDecimal price;
}
