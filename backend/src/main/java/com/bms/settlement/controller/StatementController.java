package com.bms.settlement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bms.billing.dto.ManualFeeRequest;
import com.bms.billing.dto.ReasonRequest;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.FeeMapper;
import com.bms.common.Csv;
import com.bms.common.R;
import com.bms.settlement.dto.GenerateStatementRequest;
import com.bms.settlement.entity.Invoice;
import com.bms.settlement.entity.PaymentApply;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.entity.StatementLog;
import com.bms.settlement.mapper.InvoiceMapper;
import com.bms.settlement.mapper.PaymentApplyMapper;
import com.bms.settlement.mapper.StatementLogMapper;
import com.bms.settlement.mapper.StatementMapper;
import com.bms.settlement.service.SettlementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController
@RequestMapping("/api/settlement/statement")
@RequiredArgsConstructor
public class StatementController {
    private final StatementMapper mapper;
    private final StatementLogMapper logMapper;
    private final FeeMapper feeMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentApplyMapper applyMapper;
    private final SettlementService service;

    private LambdaQueryWrapper<Statement> query(String keyword, String direction, String partnerCode, String status, LocalDate from, LocalDate to) {
        return new LambdaQueryWrapper<Statement>()
                .like(StringUtils.isNotBlank(keyword), Statement::getStatementNo, keyword)
                .eq(StringUtils.isNotBlank(direction), Statement::getDirection, direction)
                .eq(StringUtils.isNotBlank(partnerCode), Statement::getPartnerCode, partnerCode)
                .eq(StringUtils.isNotBlank(status), Statement::getStatus, status)
                .ge(from != null, Statement::getPeriodEnd, from)
                .le(to != null, Statement::getPeriodStart, to)
                .orderByDesc(Statement::getId);
    }

    @GetMapping("/page")
    public R<Page<Statement>> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
                                   @RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
                                   @RequestParam(required = false) String partnerCode, @RequestParam(required = false) String status,
                                   @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return R.ok(mapper.selectPage(new Page<>(current, size), query(keyword, direction, partnerCode, status, from, to)));
    }

    @Data
    public static class StatementDetail {
        private Statement statement;
        private List<Fee> fees;
        private List<Invoice> invoices;
        private List<PaymentApply> applies;
        private List<StatementLog> logs;
    }

    @GetMapping("/{statementNo}")
    public R<StatementDetail> get(@PathVariable String statementNo) {
        StatementDetail d = new StatementDetail();
        d.setStatement(service.require(statementNo));
        d.setFees(feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getStatementNo, statementNo).orderByAsc(Fee::getId)));
        d.setInvoices(invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>().eq(Invoice::getStatementNo, statementNo).orderByAsc(Invoice::getId)));
        d.setApplies(applyMapper.selectList(new LambdaQueryWrapper<PaymentApply>().eq(PaymentApply::getStatementNo, statementNo).orderByAsc(PaymentApply::getId)));
        d.setLogs(logMapper.selectList(new LambdaQueryWrapper<StatementLog>().eq(StatementLog::getStatementNo, statementNo).orderByAsc(StatementLog::getId)));
        return R.ok(d);
    }

    @PostMapping("/generate")
    public R<Statement> generate(@Valid @RequestBody GenerateStatementRequest req) {
        return R.ok(service.generate(req));
    }

    @PostMapping("/generate-batch")
    public R<List<Statement>> generateBatch(@RequestParam String direction, @RequestParam LocalDate periodStart, @RequestParam LocalDate periodEnd) {
        return R.ok(service.generateBatch(direction, periodStart, periodEnd));
    }

    @PostMapping("/{statementNo}/confirm")
    public R<Statement> confirm(@PathVariable String statementNo) {
        return R.ok(service.confirm(statementNo));
    }

    @PostMapping("/{statementNo}/dispute")
    public R<Statement> dispute(@PathVariable String statementNo, @RequestBody ReasonRequest req) {
        return R.ok(service.dispute(statementNo, req.getReason()));
    }

    @PostMapping("/{statementNo}/cancel")
    public R<Statement> cancel(@PathVariable String statementNo, @RequestBody(required = false) ReasonRequest req) {
        return R.ok(service.cancel(statementNo, req == null ? null : req.getReason()));
    }

    @PostMapping("/{statementNo}/adjust")
    public R<Statement> adjust(@PathVariable String statementNo, @RequestBody ManualFeeRequest req) {
        return R.ok(service.adjust(statementNo, req));
    }

    @DeleteMapping("/{statementNo}/fees/{feeNo}")
    public R<Statement> removeFee(@PathVariable String statementNo, @PathVariable String feeNo) {
        return R.ok(service.removeFee(statementNo, feeNo));
    }

    @GetMapping("/{statementNo}/export")
    public ResponseEntity<byte[]> export(@PathVariable String statementNo) {
        List<Fee> list = feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getStatementNo, statementNo).orderByAsc(Fee::getId));
        String[] headers = {"费用号", "单据号", "业务类型", "仓库", "费用项目", "业务日期", "单位", "数量", "单价", "不含税金额", "税率", "税额", "含税金额", "来源", "状态", "计算过程", "备注"};
        return Csv.download(statementNo + ".csv", headers, list, f -> new Object[]{f.getFeeNo(), f.getDocNo(), f.getBizType(), f.getWarehouseCode(),
                f.getChargeItemCode(), f.getBizDate(), f.getUnit(), f.getQty(), f.getUnitPrice(), f.getAmount(), f.getTaxRate(), f.getTaxAmount(),
                f.getTotalAmount(), f.getSource(), f.getStatus(), f.getCalcDetail(), f.getRemark()});
    }
}
