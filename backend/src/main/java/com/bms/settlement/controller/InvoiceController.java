package com.bms.settlement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bms.billing.dto.ReasonRequest;
import com.bms.common.R;
import com.bms.settlement.entity.Invoice;
import com.bms.settlement.mapper.InvoiceMapper;
import com.bms.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlement/invoice")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceMapper mapper;
    private final SettlementService service;

    @GetMapping("/page")
    public R<Page<Invoice>> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
                                 @RequestParam(required = false) String partnerCode, @RequestParam(required = false) String status) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Invoice>()
                .and(StringUtils.isNotBlank(keyword), w -> w.like(Invoice::getInvoiceNo, keyword).or().like(Invoice::getInvoiceCode, keyword).or().like(Invoice::getStatementNo, keyword))
                .eq(StringUtils.isNotBlank(direction), Invoice::getDirection, direction)
                .eq(StringUtils.isNotBlank(partnerCode), Invoice::getPartnerCode, partnerCode)
                .eq(StringUtils.isNotBlank(status), Invoice::getStatus, status)
                .orderByDesc(Invoice::getId)));
    }

    @PostMapping
    public R<Invoice> issue(@RequestBody Invoice invoice) {
        return R.ok(service.issueInvoice(invoice));
    }

    @PostMapping("/{invoiceNo}/cancel")
    public R<Invoice> cancel(@PathVariable String invoiceNo, @RequestBody(required = false) ReasonRequest req) {
        return R.ok(service.cancelInvoice(invoiceNo, req == null ? null : req.getReason()));
    }
}
