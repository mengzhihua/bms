package com.bms.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.common.SchemaMigration;
import com.bms.settlement.dto.GenerateStatementRequest;
import com.bms.settlement.dto.PaymentApplyRequest;
import com.bms.settlement.entity.Invoice;
import com.bms.settlement.entity.Payment;
import com.bms.settlement.entity.PaymentApply;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.mapper.PaymentApplyMapper;
import com.bms.settlement.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** PR #2 Devin Review 二轮：升级迁移 / 重复反核销 / 重复作废发票 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewFix2Test {

    @Autowired BillingService billingService;
    @Autowired SettlementService settlementService;
    @Autowired PaymentApplyMapper applyMapper;
    @Autowired SchemaMigration schemaMigration;
    @Autowired JdbcTemplate jdbc;

    private static BizDoc doc(String bizType, LocalDate date) {
        BizDoc d = new BizDoc();
        d.setSource("WMS");
        d.setExtRef("R2-" + UUID.randomUUID());
        d.setBizType(bizType);
        d.setCustomerCode("CUST-001");
        d.setWarehouseCode("WH-SH");
        d.setBizDate(date);
        d.setOrders(1);
        return d;
    }

    private Statement confirmedStatement(LocalDate date) {
        billingService.createDoc(doc("RETURN", date), true);
        GenerateStatementRequest req = new GenerateStatementRequest();
        req.setDirection("AR");
        req.setPartnerCode("CUST-001");
        req.setPeriodStart(date);
        req.setPeriodEnd(date);
        Statement s = settlementService.generate(req);
        return settlementService.confirm(s.getStatementNo());
    }

    @Test
    void migrationUpgradesLegacyIndexToUnique() throws Exception {
        // 模拟 PR 之前的库：只有普通索引 idx_biz_doc_ref
        jdbc.execute("ALTER TABLE bms_biz_doc DROP CONSTRAINT uk_biz_doc_ref");
        jdbc.execute("CREATE INDEX idx_biz_doc_ref ON bms_biz_doc (source, ext_ref)");
        String ref = "DUP-" + UUID.randomUUID();
        jdbc.update("INSERT INTO bms_biz_doc (doc_no, source, ext_ref, biz_type, customer_code, biz_date, bill_status) VALUES (?,?,?,?,?,?,?)",
                "MIG-" + UUID.randomUUID().toString().substring(0, 12), "WMS", ref, "RETURN", "CUST-001", LocalDate.of(2030, 1, 1), "PENDING");
        jdbc.update("INSERT INTO bms_biz_doc (doc_no, source, ext_ref, biz_type, customer_code, biz_date, bill_status) VALUES (?,?,?,?,?,?,?)",
                "MIG-" + UUID.randomUUID().toString().substring(0, 12), "WMS", ref, "RETURN", "CUST-001", LocalDate.of(2030, 1, 1), "PENDING");

        // 存在重复数据时不创建唯一索引，仅报错日志
        schemaMigration.run(new DefaultApplicationArguments());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE lower(index_name) LIKE 'uk_biz_doc_ref%'", Integer.class));

        jdbc.update("DELETE FROM bms_biz_doc WHERE ext_ref = ?", ref);
        schemaMigration.run(new DefaultApplicationArguments());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.indexes WHERE lower(index_name) = 'idx_biz_doc_ref'", Integer.class));
        assertThrows(DuplicateKeyException.class, () -> {
            jdbc.update("INSERT INTO bms_biz_doc (doc_no, source, ext_ref, biz_type, customer_code, biz_date, bill_status) VALUES (?,?,?,?,?,?,?)",
                    "MIG-" + UUID.randomUUID().toString().substring(0, 12), "WMS", ref, "RETURN", "CUST-001", LocalDate.of(2030, 1, 1), "PENDING");
            jdbc.update("INSERT INTO bms_biz_doc (doc_no, source, ext_ref, biz_type, customer_code, biz_date, bill_status) VALUES (?,?,?,?,?,?,?)",
                    "MIG-" + UUID.randomUUID().toString().substring(0, 12), "WMS", ref, "RETURN", "CUST-001", LocalDate.of(2030, 1, 1), "PENDING");
        });
        // 再次运行应为空操作
        schemaMigration.run(new DefaultApplicationArguments());
    }

    @Test
    void unapplyIsRejectedOnceApplyRecordIsGone() {
        Statement s = confirmedStatement(LocalDate.of(2028, 8, 11));
        Payment p = new Payment();
        p.setDirection("AR");
        p.setPartnerCode("CUST-001");
        p.setAmount(new BigDecimal("10"));
        p = settlementService.createPayment(p);
        PaymentApplyRequest req = new PaymentApplyRequest();
        req.setPaymentNo(p.getPaymentNo());
        req.setStatementNo(s.getStatementNo());
        req.setAmount(new BigDecimal("1"));
        settlementService.apply(req);
        PaymentApply a = applyMapper.selectOne(new LambdaQueryWrapper<PaymentApply>()
                .eq(PaymentApply::getPaymentNo, p.getPaymentNo()));

        // 核销记录已被另一事务删除（模拟竞争中读到旧快照）
        applyMapper.deleteById(a.getId());
        assertThrows(BizException.class, () -> settlementService.unapply(a.getId()));
        assertEquals(new BigDecimal("1.00"), settlementService.requirePayment(p.getPaymentNo()).getAppliedAmount());
        assertEquals(new BigDecimal("1.00"), settlementService.require(s.getStatementNo()).getPaidAmount());
    }

    @Test
    void cancelInvoiceOnlyDeductsOnce() {
        Statement s = confirmedStatement(LocalDate.of(2028, 8, 12));
        Invoice inv = new Invoice();
        inv.setStatementNo(s.getStatementNo());
        inv.setTotalAmount(new BigDecimal("1"));
        Invoice issued = settlementService.issueInvoice(inv);
        BigDecimal before = settlementService.require(s.getStatementNo()).getInvoicedAmount();

        // 直接把发票置为已作废（模拟并发作废先一步提交）
        jdbc.update("UPDATE bms_invoice SET status = 'CANCELLED' WHERE invoice_no = ?", issued.getInvoiceNo());
        assertThrows(BizException.class, () -> settlementService.cancelInvoice(issued.getInvoiceNo(), "dup"));
        assertEquals(before, settlementService.require(s.getStatementNo()).getInvoicedAmount());
    }
}
