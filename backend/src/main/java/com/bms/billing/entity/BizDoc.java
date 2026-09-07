package com.bms.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 业务单据（计费来源）。bizType: INBOUND/OUTBOUND/STORAGE/TRANSPORT/VAS/RETURN; billStatus: PENDING/BILLED/FAILED/IGNORED */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_biz_doc")
public class BizDoc extends BaseEntity {
    private String docNo;
    private String source;
    private String extRef;
    private String bizType;
    private String customerCode;
    private String supplierCode;
    private String warehouseCode;
    private LocalDate bizDate;
    private Integer orders;
    private Integer lines;
    private BigDecimal qty;
    private BigDecimal boxes;
    private BigDecimal pallets;
    private BigDecimal weight;
    private BigDecimal volume;
    private BigDecimal distance;
    private Integer days;
    private String origin;
    private String destination;
    private String billStatus;
    private String failReason;
    private BigDecimal arAmount;
    private BigDecimal apAmount;
    private String remark;
}
