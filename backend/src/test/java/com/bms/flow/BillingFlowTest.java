package com.bms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bms.billing.dto.ManualFeeRequest;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.settlement.dto.GenerateStatementRequest;
import com.bms.settlement.dto.PaymentApplyRequest;
import com.bms.settlement.entity.Invoice;
import com.bms.settlement.entity.Payment;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 计费结算全链路：业务单据 -> 自动计费(AR/AP) -> 对账单 -> 确认 -> 开票 -> 收款核销 -> 结清。 */
@SpringBootTest
@ActiveProfiles("test")
class BillingFlowTest {

    @Autowired BillingService billingService;
    @Autowired SettlementService settlementService;
    @Autowired FeeMapper feeMapper;

    /** 每个用例使用独立的远期业务日期，避免账期互相干扰 */
    private static LocalDate isolatedDate() {
        return LocalDate.of(2027, 1, 1).plusDays((long) (Math.random() * 300));
    }

    private static BizDoc doc(String bizType, String customer, String supplier, String wh, LocalDate date) {
        BizDoc d = new BizDoc();
        d.setSource("WMS");
        d.setExtRef("T-" + UUID.randomUUID());
        d.setBizType(bizType);
        d.setCustomerCode(customer);
        d.setSupplierCode(supplier);
        d.setWarehouseCode(wh);
        d.setBizDate(date);
        d.setOrders(1);
        return d;
    }

    private List<Fee> fees(String docNo) {
        return feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, docNo).orderByAsc(Fee::getChargeItemCode));
    }

    @Test
    void outboundTieredAndFixed() {
        BizDoc d = doc("OUTBOUND", "CUST-001", null, "WH-SH", isolatedDate());
        d.setQty(new BigDecimal("25"));
        d = billingService.createDoc(d, true);
        assertEquals(BillingService.BILLED, d.getBillStatus(), d.getFailReason());
        List<Fee> fees = fees(d.getDocNo());
        assertEquals(2, fees.size());
        // ORDER_FEE 固定 2 元；OUTBOUND_HANDLING 阶梯 25 件落在 10-100 档 0.3 元/件 = 7.5
        assertEquals(new BigDecimal("2.00"), fees.get(0).getAmount());
        assertEquals(new BigDecimal("7.50"), fees.get(1).getAmount());
        // 合同税率 6%：(2 + 7.5) * 1.06 = 10.07
        assertEquals(new BigDecimal("10.07"), d.getArAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), d.getApAmount().setScale(2));
    }

    @Test
    void transportProgressiveArAndUnitAp() {
        BizDoc d = doc("TRANSPORT", "CUST-001", "SF", null, isolatedDate());
        d.setWeight(new BigDecimal("12.5"));
        d = billingService.createDoc(d, true);
        assertEquals(BillingService.BILLED, d.getBillStatus(), d.getFailReason());
        // AR 累进：首 1kg 8 + 9kg × 2 + 2.5kg × 1.5 = 29.75 → 含税 6% = 31.54
        Fee ar = fees(d.getDocNo()).stream().filter(f -> "AR".equals(f.getDirection())).findFirst().orElseThrow(AssertionError::new);
        assertEquals(new BigDecimal("29.75"), ar.getAmount());
        assertEquals(new BigDecimal("31.54"), d.getArAmount());
        // AP 顺丰：运费 1.2 × 12.5 = 15 + 派送 1 = 16 → 含税 9% = 17.44
        assertEquals(new BigDecimal("17.44"), d.getApAmount());
    }

    @Test
    void storageMinChargeAndIdempotentPush() {
        BizDoc d = doc("STORAGE", "CUST-001", null, "WH-SH", isolatedDate());
        d.setPallets(new BigDecimal("10"));
        d.setDays(3);
        String ref = d.getExtRef();
        d = billingService.createDoc(d, true);
        // 0.8 × 10 托 × 3 天 = 24 < 最低 50
        Fee f = fees(d.getDocNo()).get(0);
        assertEquals(new BigDecimal("50.00"), f.getAmount());
        assertTrue(f.getCalcDetail().contains("最低收费"));

        BizDoc again = doc("STORAGE", "CUST-001", null, "WH-SH", d.getBizDate());
        again.setExtRef(ref);
        assertEquals(d.getDocNo(), billingService.createDoc(again, true).getDocNo());
    }

    @Test
    void noContractFailsAndRebillAfterFix() {
        // CUST-003 无生效合同
        BizDoc d = doc("OUTBOUND", "CUST-003", null, "WH-GZ", isolatedDate());
        d.setQty(BigDecimal.ONE);
        d = billingService.createDoc(d, true);
        assertEquals(BillingService.FAILED, d.getBillStatus());
        assertTrue(d.getFailReason().contains("无生效合同"));
        assertEquals(0, fees(d.getDocNo()).size());
        assertThrows(BizException.class, () -> billingService.createDoc(doc("OUTBOUND", "NOPE", null, null, LocalDate.now()), false));
    }

    @Test
    void statementLifecycle() {
        LocalDate date = isolatedDate();
        BizDoc d1 = doc("OUTBOUND", "CUST-001", null, "WH-SH", date);
        d1.setQty(new BigDecimal("5"));
        d1 = billingService.createDoc(d1, true); // 2 + 2.5 = 4.5 → 4.77
        BizDoc d2 = billingService.createDoc(doc("RETURN", "CUST-001", null, "WH-SH", date), true); // 3 → 3.18

        GenerateStatementRequest req = new GenerateStatementRequest();
        req.setDirection("AR");
        req.setPartnerCode("CUST-001");
        req.setPeriodStart(date);
        req.setPeriodEnd(date);
        Statement s = settlementService.generate(req);
        assertEquals(SettlementService.DRAFT, s.getStatus());
        assertEquals(3, s.getFeeCount());
        assertEquals(new BigDecimal("7.95"), s.getTotalAmount());
        assertEquals(date.plusDays(30), s.getDueDate());
        assertEquals(BillingService.FEE_STATEMENTED, billingService.requireFee(fees(d2.getDocNo()).get(0).getFeeNo()).getStatus());
        assertThrows(BizException.class, () -> settlementService.generate(req));

        // 争议 -> 折让 -2 -> 确认
        settlementService.dispute(s.getStatementNo(), "客户质疑退货费");
        ManualFeeRequest adj = new ManualFeeRequest();
        adj.setChargeItemCode("OTHER");
        adj.setAmount(new BigDecimal("-2"));
        adj.setTaxRate(BigDecimal.ZERO);
        adj.setRemark("折让");
        Statement adjusted = settlementService.adjust(s.getStatementNo(), adj);
        assertEquals(new BigDecimal("5.95"), adjusted.getTotalAmount());
        assertEquals(new BigDecimal("-2.00"), adjusted.getAdjustAmount());
        Statement confirmed = settlementService.confirm(s.getStatementNo());
        assertEquals(SettlementService.CONFIRMED, confirmed.getStatus());
        assertThrows(BizException.class, () -> settlementService.removeFee(s.getStatementNo(), fees(d2.getDocNo()).get(0).getFeeNo()));

        // 开票不能超额
        Invoice inv = new Invoice();
        inv.setStatementNo(s.getStatementNo());
        inv.setTotalAmount(new BigDecimal("100"));
        assertThrows(BizException.class, () -> settlementService.issueInvoice(inv));
        inv.setTotalAmount(new BigDecimal("5.95"));
        Invoice issued = settlementService.issueInvoice(inv);
        assertEquals("AR", issued.getDirection());
        assertEquals(new BigDecimal("5.95"), settlementService.require(s.getStatementNo()).getInvoicedAmount());

        // 收款 10 元，核销 5.95 -> 结清；剩余 4.05 未核销
        Payment p = new Payment();
        p.setDirection("AR");
        p.setPartnerCode("CUST-001");
        p.setAmount(new BigDecimal("10"));
        p = settlementService.createPayment(p);
        PaymentApplyRequest ar = new PaymentApplyRequest();
        ar.setPaymentNo(p.getPaymentNo());
        ar.setStatementNo(s.getStatementNo());
        p = settlementService.apply(ar);
        assertEquals(new BigDecimal("5.95"), p.getAppliedAmount());
        Statement settled = settlementService.require(s.getStatementNo());
        assertEquals(SettlementService.SETTLED, settled.getStatus());
        assertEquals(BillingService.FEE_SETTLED, billingService.requireFee(fees(d1.getDocNo()).get(0).getFeeNo()).getStatus());
        String paymentNo = p.getPaymentNo();
        assertThrows(BizException.class, () -> settlementService.deletePayment(paymentNo));
        // 已结清单据不允许重算
        String docNo = d1.getDocNo();
        assertThrows(BizException.class, () -> billingService.bill(docNo));
    }

    @Test
    void cancelStatementReleasesFees() {
        LocalDate date = isolatedDate();
        BizDoc d = doc("INBOUND", "CUST-002", "SUP-LABOR", "WH-BJ", date);
        d.setPallets(new BigDecimal("2"));
        d.setQty(new BigDecimal("100"));
        d = billingService.createDoc(d, true);
        assertEquals(BillingService.BILLED, d.getBillStatus(), d.getFailReason());
        // AR 15 × 2 托 = 30；AP 人力 0.2 × 100 件 = 20
        assertEquals(new BigDecimal("30.00"), fees(d.getDocNo()).stream().filter(f -> "AR".equals(f.getDirection())).findFirst().get().getAmount());
        assertEquals(new BigDecimal("20.00"), fees(d.getDocNo()).stream().filter(f -> "AP".equals(f.getDirection())).findFirst().get().getAmount());

        GenerateStatementRequest req = new GenerateStatementRequest();
        req.setDirection("AP");
        req.setPartnerCode("SUP-LABOR");
        req.setPeriodStart(date);
        req.setPeriodEnd(date);
        Statement s = settlementService.generate(req);
        settlementService.cancel(s.getStatementNo(), "重新对账");
        assertEquals(SettlementService.CANCELLED, settlementService.require(s.getStatementNo()).getStatus());
        Fee ap = fees(d.getDocNo()).stream().filter(f -> "AP".equals(f.getDirection())).findFirst().get();
        assertEquals(BillingService.FEE_NEW, ap.getStatus());
        assertNull(ap.getStatementNo());
        // 释放后可再次生成
        assertEquals(SettlementService.DRAFT, settlementService.generate(req).getStatus());
    }
}
