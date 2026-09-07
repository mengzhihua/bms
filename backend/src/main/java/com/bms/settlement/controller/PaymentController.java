package com.bms.settlement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bms.common.R;
import com.bms.settlement.dto.PaymentApplyRequest;
import com.bms.settlement.entity.Payment;
import com.bms.settlement.entity.PaymentApply;
import com.bms.settlement.mapper.PaymentApplyMapper;
import com.bms.settlement.mapper.PaymentMapper;
import com.bms.settlement.service.SettlementService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/settlement/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentMapper mapper;
    private final PaymentApplyMapper applyMapper;
    private final SettlementService service;

    @GetMapping("/page")
    public R<Page<Payment>> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
                                 @RequestParam(required = false) String keyword, @RequestParam(required = false) String direction,
                                 @RequestParam(required = false) String partnerCode, @RequestParam(required = false) Boolean unapplied) {
        return R.ok(mapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<Payment>()
                .and(StringUtils.isNotBlank(keyword), w -> w.like(Payment::getPaymentNo, keyword).or().like(Payment::getBankRef, keyword))
                .eq(StringUtils.isNotBlank(direction), Payment::getDirection, direction)
                .eq(StringUtils.isNotBlank(partnerCode), Payment::getPartnerCode, partnerCode)
                .apply(Boolean.TRUE.equals(unapplied), "applied_amount < amount")
                .orderByDesc(Payment::getId)));
    }

    @Data
    public static class PaymentDetail {
        private Payment payment;
        private List<PaymentApply> applies;
    }

    @GetMapping("/{paymentNo}")
    public R<PaymentDetail> get(@PathVariable String paymentNo) {
        PaymentDetail d = new PaymentDetail();
        d.setPayment(service.requirePayment(paymentNo));
        d.setApplies(applyMapper.selectList(new LambdaQueryWrapper<PaymentApply>().eq(PaymentApply::getPaymentNo, paymentNo).orderByAsc(PaymentApply::getId)));
        return R.ok(d);
    }

    @PostMapping
    public R<Payment> create(@RequestBody Payment payment) {
        return R.ok(service.createPayment(payment));
    }

    @DeleteMapping("/{paymentNo}")
    public R<Void> delete(@PathVariable String paymentNo) {
        service.deletePayment(paymentNo);
        return R.ok(null);
    }

    @PostMapping("/apply")
    public R<Payment> apply(@Valid @RequestBody PaymentApplyRequest req) {
        return R.ok(service.apply(req));
    }

    @DeleteMapping("/apply/{applyId}")
    public R<Payment> unapply(@PathVariable Long applyId) {
        return R.ok(service.unapply(applyId));
    }
}
