package com.bms.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 费率规则。priceMode: FIXED 每单固定 / UNIT 单价×数量 / TIERED 阶梯全量 / PROGRESSIVE 累进 / FIRST_EXTRA 首重续重 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_rate_rule")
public class RateRule extends BaseEntity {
    private Long contractId;
    private String chargeItemCode;
    private String bizType;
    private String warehouseCode;
    private String unit;
    private String priceMode;
    private BigDecimal unitPrice;
    private BigDecimal minCharge;
    private BigDecimal maxCharge;
    private Integer priority;
    private Integer status;
    private String remark;
}
