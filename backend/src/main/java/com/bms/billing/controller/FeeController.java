package com.bms.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bms.billing.dto.ManualFeeRequest;
import com.bms.billing.dto.ReasonRequest;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.Csv;
import com.bms.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/fee")
@RequiredArgsConstructor
public class FeeController {
    private final FeeMapper mapper;
    private final BillingService service;

    private LambdaQueryWrapper<Fee> query(String keyword, String direction, String partnerCode, String chargeItemCode, String status,
                                          String source, String statementNo, LocalDate from, LocalDate to) {
        return new LambdaQueryWrapper<Fee>()
                .and(StringUtils.isNotBlank(keyword), w -> w.like(Fee::getFeeNo, keyword).or().like(Fee::getDocNo, keyword).or().like(Fee::getContractNo, keyword))
                .eq(StringUtils.isNotBlank(direction), Fee::getDirection, direction)
                .eq(StringUtils.isNotBlank(partnerCode), Fee::getPartnerCode, partnerCode)
                .eq(StringUtils.isNotBlank(chargeItemCode), Fee::getChargeItemCode, chargeItemCode)
                .eq(StringUtils.isNotBlank(status), Fee::getStatus, status)
                .eq(StringUtils.isNotBlank(source), Fee::getSource, source)
                .eq(StringUtils.isNotBlank(statementNo), Fee::getStatementNo, statementNo)
                .ge(from != null, Fee::getBizDate, from)
                .le(to != null, Fee::getBizDate, to)
                .orderByDesc(Fee::getId);
    }

    @GetMapping("/page")
    public R<Page<Fee>> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
                             @RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
                             @RequestParam(required = false) String partnerCode, @RequestParam(required = false) String chargeItemCode,
                             @RequestParam(required = false) String status, @RequestParam(required = false) String source,
                             @RequestParam(required = false) String statementNo,
                             @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return R.ok(mapper.selectPage(new Page<>(current, size), query(keyword, direction, partnerCode, chargeItemCode, status, source, statementNo, from, to)));
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) String direction, @RequestParam(required = false) String partnerCode,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return R.ok(mapper.summary(direction, partnerCode, status, from, to));
    }

    @GetMapping("/{feeNo}")
    public R<Fee> get(@PathVariable String feeNo) {
        return R.ok(service.requireFee(feeNo));
    }

    @PostMapping("/manual")
    public R<Fee> manual(@Valid @RequestBody ManualFeeRequest req) {
        req.setStatementNo(null);
        return R.ok(service.manualFee(req, "MANUAL"));
    }

    @PostMapping("/{feeNo}/cancel")
    public R<Fee> cancel(@PathVariable String feeNo, @RequestBody(required = false) ReasonRequest req) {
        return R.ok(service.cancelFee(feeNo, req == null ? null : req.getReason()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
                                         @RequestParam(required = false) String partnerCode, @RequestParam(required = false) String chargeItemCode,
                                         @RequestParam(required = false) String status, @RequestParam(required = false) String source,
                                         @RequestParam(required = false) String statementNo,
                                         @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        List<Fee> list = mapper.selectList(query(keyword, direction, partnerCode, chargeItemCode, status, source, statementNo, from, to));
        String[] headers = {"费用号", "方向", "结算对象", "合同号", "单据号", "业务类型", "仓库", "费用项目", "业务日期", "单位", "数量", "单价", "不含税金额", "税率", "税额", "含税金额", "来源", "状态", "对账单号", "计算过程", "备注"};
        return Csv.download("fees.csv", headers, list, f -> new Object[]{f.getFeeNo(), f.getDirection(), f.getPartnerCode(), f.getContractNo(),
                f.getDocNo(), f.getBizType(), f.getWarehouseCode(), f.getChargeItemCode(), f.getBizDate(), f.getUnit(), f.getQty(), f.getUnitPrice(),
                f.getAmount(), f.getTaxRate(), f.getTaxAmount(), f.getTotalAmount(), f.getSource(), f.getStatus(), f.getStatementNo(), f.getCalcDetail(), f.getRemark()});
    }
}
