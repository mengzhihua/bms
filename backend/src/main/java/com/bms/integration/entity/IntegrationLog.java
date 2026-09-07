package com.bms.integration.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bms.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bms_integration_log")
public class IntegrationLog extends BaseEntity {
    private String direction;
    private String target;
    private String action;
    private String refNo;
    private String requestBody;
    private String responseBody;
    private Integer success;
    private String errorMsg;
}
