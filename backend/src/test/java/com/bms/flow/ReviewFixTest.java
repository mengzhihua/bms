package com.bms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.BizDocMapper;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.common.Csv;
import com.bms.contract.dto.RateRuleDetail;
import com.bms.contract.entity.RateRule;
import com.bms.settlement.dto.GenerateStatementRequest;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 一致性 / 并发 / 幂等相关回归用例 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewFixTest {

    @Autowired BillingService billingService;
    @Autowired SettlementService settlementService;
    @Autowired FeeMapper feeMapper;
    @Autowired BizDocMapper docMapper;

    private static BizDoc doc(String bizType, String customer, String supplier, String wh, LocalDate date) {
        BizDoc d = new BizDoc();
        d.setSource("WMS");
        d.setExtRef("R-" + UUID.randomUUID());
        d.setBizType(bizType);
        d.setCustomerCode(customer);
        d.setSupplierCode(supplier);
        d.setWarehouseCode(wh);
        d.setBizDate(date);
        d.setOrders(1);
        return d;
    }

    private static RateRuleDetail rule(String item, String wh, Integer priority) {
        RateRule r = new RateRule();
        r.setChargeItemCode(item);
        r.setBizType("OUTBOUND");
        r.setWarehouseCode(wh);
        r.setPriority(priority);
        r.setStatus(1);
        RateRuleDetail d = new RateRuleDetail();
        d.setRule(r);
        d.setTiers(Collections.emptyList());
        return d;
    }

    @Test
    void failedBillingLeavesNoFees() {
        // AR(CUST-001) 运费可计，AP(SUP-LABOR) 合同无 TRANSPORT 费率 -> 整单失败，不能残留 AR 费用
        BizDoc d = doc("TRANSPORT", "CUST-001", "SUP-LABOR", null, LocalDate.of(2028, 5, 1));
        d.setWeight(new BigDecimal("3"));
        d = billingService.createDoc(d, true);
        assertEquals(BillingService.FAILED, d.getBillStatus());
        assertEquals(0, feeMapper.selectCount(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, d.getDocNo())));
        assertEquals(0, d.getArAmount().signum());
        assertEquals(0, d.getApAmount().signum());
    }

    @Test
    void onlyBestRulePerChargeItemIsUsed() {
        BizDoc d = doc("OUTBOUND", "CUST-001", null, "WH-SH", LocalDate.of(2028, 5, 2));
        List<RateRuleDetail> rules = Arrays.asList(
                rule("ORDER_FEE", null, 1),
                rule("ORDER_FEE", "WH-SH", 1),
                rule("ORDER_FEE", "WH-GZ", 9),
                rule("HANDLING", null, 5),
                rule("HANDLING", null, 8));
        List<RateRuleDetail> picked = BillingService.selectRules(rules, d);
        assertEquals(2, picked.size());
        assertEquals("WH-SH", picked.get(0).getRule().getWarehouseCode());
        assertEquals(8, picked.get(1).getRule().getPriority());
    }

    @Test
    void concurrentPushSameRefCreatesSingleDoc() throws Exception {
        String ref = "DUP-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<BizDoc>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tasks.add(() -> {
                    BizDoc d = doc("OUTBOUND", "CUST-001", null, "WH-SH", LocalDate.of(2028, 5, 3));
                    d.setExtRef(ref);
                    d.setQty(new BigDecimal("5"));
                    return billingService.createDoc(d, true);
                });
            }
            List<String> docNos = new ArrayList<>();
            for (Future<BizDoc> f : pool.invokeAll(tasks)) {
                docNos.add(f.get().getDocNo());
            }
            assertEquals(1, docNos.stream().distinct().count(), docNos.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, docMapper.selectCount(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getSource, "WMS").eq(BizDoc::getExtRef, ref)));
    }

    @Test
    void concurrentStatementGenerationNeverSharesFees() throws Exception {
        LocalDate date = LocalDate.of(2028, 6, 15);
        for (int i = 0; i < 3; i++) {
            BizDoc d = doc("OUTBOUND", "CUST-002", null, "WH-BJ", date);
            d.setPallets(new BigDecimal("2"));
            assertEquals(BillingService.BILLED, billingService.createDoc(d, true).getBillStatus());
        }
        GenerateStatementRequest req = new GenerateStatementRequest();
        req.setDirection("AR");
        req.setPartnerCode("CUST-002");
        req.setPeriodStart(date);
        req.setPeriodEnd(date);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Statement> ok = new ArrayList<>();
        try {
            List<Callable<Statement>> tasks = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tasks.add(() -> settlementService.generate(req));
            }
            for (Future<Statement> f : pool.invokeAll(tasks)) {
                try {
                    ok.add(f.get());
                } catch (java.util.concurrent.ExecutionException e) {
                    assertTrue(e.getCause() instanceof BizException, String.valueOf(e.getCause()));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertFalse(ok.isEmpty());
        BigDecimal statementTotal = ok.stream().map(Statement::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>()
                .eq(Fee::getDirection, "AR").eq(Fee::getPartnerCode, "CUST-002").eq(Fee::getBizDate, date));
        BigDecimal feeTotal = fees.stream().map(Fee::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(feeTotal, statementTotal);
        List<String> stmtNos = ok.stream().map(Statement::getStatementNo).collect(Collectors.toList());
        for (Fee f : fees) {
            assertEquals(BillingService.FEE_STATEMENTED, f.getStatus());
            assertTrue(stmtNos.contains(f.getStatementNo()));
        }
    }

    @Test
    void csvRoundTripsQuotedNewlines() throws Exception {
        byte[] exported = Csv.download("t.csv", new String[]{"code", "remark"},
                Arrays.asList(new String[]{"A1", "line1\nline2, with \"quote\""}, new String[]{"A2", "plain"}),
                r -> r).getBody();
        List<String[]> rows = Csv.read(new ByteArrayInputStream(exported));
        assertEquals(3, rows.size());
        assertEquals("code", rows.get(0)[0]);
        assertEquals("line1\nline2, with \"quote\"", rows.get(1)[1]);
        assertEquals("A2", rows.get(2)[0]);
    }
}
