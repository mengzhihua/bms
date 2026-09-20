package com.bms.settlement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bms.basic.entity.Partner;
import com.bms.basic.mapper.PartnerMapper;
import com.bms.billing.dto.ManualFeeRequest;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.common.CodeGenerator;
import com.bms.contract.entity.Contract;
import com.bms.contract.service.ContractService;
import com.bms.settlement.dto.GenerateStatementRequest;
import com.bms.settlement.dto.PaymentApplyRequest;
import com.bms.settlement.entity.Invoice;
import com.bms.settlement.entity.Payment;
import com.bms.settlement.entity.PaymentApply;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.entity.StatementLog;
import com.bms.settlement.mapper.InvoiceMapper;
import com.bms.settlement.mapper.PaymentApplyMapper;
import com.bms.settlement.mapper.PaymentMapper;
import com.bms.settlement.mapper.StatementLogMapper;
import com.bms.settlement.mapper.StatementMapper;
import com.bms.system.auth.CurrentUser;
import com.bms.system.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementService {
    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String DISPUTED = "DISPUTED";
    public static final String SETTLED = "SETTLED";
    public static final String CANCELLED = "CANCELLED";

    private final StatementMapper statementMapper;
    private final StatementLogMapper logMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentApplyMapper applyMapper;
    private final FeeMapper feeMapper;
    private final PartnerMapper partnerMapper;
    private final ContractService contractService;
    private final BillingService billingService;
    private final CodeGenerator codeGenerator;

    private static String operator() {
        User u = CurrentUser.get();
        return u == null ? "system" : u.getUsername();
    }

    public Statement require(String statementNo) {
        return require(statementNo, false);
    }

    /** lock=true 时以 SELECT ... FOR UPDATE 读取，保证同一对账单的开票/核销串行执行 */
    private Statement require(String statementNo, boolean lock) {
        LambdaQueryWrapper<Statement> q = new LambdaQueryWrapper<Statement>().eq(Statement::getStatementNo, statementNo);
        if (lock) {
            q.last("for update");
        }
        Statement s = statementMapper.selectOne(q);
        if (s == null) {
            throw new BizException("对账单不存在: " + statementNo);
        }
        return s;
    }

    private void log(String no, String action, String from, String to, String remark) {
        StatementLog l = new StatementLog();
        l.setStatementNo(no);
        l.setAction(action);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperator(operator());
        l.setRemark(remark);
        l.setCreatedAt(LocalDateTime.now());
        logMapper.insert(l);
    }

    // ---------------- 对账单 ----------------

    /** 按结算对象 + 期间 汇总未对账费用生成对账单 */
    @Transactional
    public Statement generate(GenerateStatementRequest req) {
        if (!"AR".equals(req.getDirection()) && !"AP".equals(req.getDirection())) {
            throw new BizException("方向必须为 AR / AP");
        }
        if (req.getPeriodStart() == null || req.getPeriodEnd() == null || req.getPeriodEnd().isBefore(req.getPeriodStart())) {
            throw new BizException("账期区间无效");
        }
        Partner p = partnerMapper.selectOne(new LambdaQueryWrapper<Partner>().eq(Partner::getCode, req.getPartnerCode()));
        if (p == null) {
            throw new BizException("结算对象不存在: " + req.getPartnerCode());
        }
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>()
                .eq(Fee::getDirection, req.getDirection()).eq(Fee::getPartnerCode, req.getPartnerCode())
                .eq(Fee::getStatus, BillingService.FEE_NEW)
                .ge(Fee::getBizDate, req.getPeriodStart()).le(Fee::getBizDate, req.getPeriodEnd()));
        if (fees.isEmpty()) {
            throw new BizException("该期间没有未对账费用");
        }
        Statement s = new Statement();
        s.setStatementNo(codeGenerator.next("AR".equals(req.getDirection()) ? "ST" : "SP"));
        s.setDirection(req.getDirection());
        s.setPartnerCode(req.getPartnerCode());
        s.setPeriodStart(req.getPeriodStart());
        s.setPeriodEnd(req.getPeriodEnd());
        s.setStatus(DRAFT);
        s.setAdjustAmount(BigDecimal.ZERO);
        s.setPaidAmount(BigDecimal.ZERO);
        s.setInvoicedAmount(BigDecimal.ZERO);
        s.setRemark(req.getRemark());
        int paymentDays = 30;
        List<Contract> contracts = contractService.effectiveContracts(req.getDirection(), req.getPartnerCode(), req.getPeriodEnd());
        if (!contracts.isEmpty() && contracts.get(0).getPaymentDays() != null) {
            paymentDays = contracts.get(0).getPaymentDays();
        }
        s.setDueDate(req.getPeriodEnd().plusDays(paymentDays));
        statementMapper.insert(s);
        int claimed = 0;
        for (Fee f : fees) {
            claimed += feeMapper.update(null, new LambdaUpdateWrapper<Fee>()
                    .eq(Fee::getId, f.getId()).eq(Fee::getStatus, BillingService.FEE_NEW).isNull(Fee::getStatementNo)
                    .set(Fee::getStatementNo, s.getStatementNo()).set(Fee::getStatus, BillingService.FEE_STATEMENTED));
        }
        if (claimed == 0) {
            throw new BizException("该期间没有未对账费用");
        }
        recalc(s);
        log(s.getStatementNo(), "GENERATE", null, DRAFT, claimed + " 条费用");
        return s;
    }

    /** 批量生成：期间内所有有未对账费用的结算对象各生成一张 */
    @Transactional
    public List<Statement> generateBatch(String direction, LocalDate start, LocalDate end) {
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>()
                .select(Fee::getPartnerCode)
                .eq(Fee::getDirection, direction).eq(Fee::getStatus, BillingService.FEE_NEW)
                .ge(Fee::getBizDate, start).le(Fee::getBizDate, end).groupBy(Fee::getPartnerCode));
        List<Statement> out = new ArrayList<>();
        for (Fee f : fees) {
            GenerateStatementRequest r = new GenerateStatementRequest();
            r.setDirection(direction);
            r.setPartnerCode(f.getPartnerCode());
            r.setPeriodStart(start);
            r.setPeriodEnd(end);
            out.add(generate(r));
        }
        return out;
    }

    /** 根据明细重算对账单金额 */
    private void recalc(Statement s) {
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>()
                .eq(Fee::getStatementNo, s.getStatementNo()).ne(Fee::getStatus, BillingService.FEE_CANCELLED));
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal adjust = BigDecimal.ZERO;
        for (Fee f : fees) {
            amount = amount.add(f.getAmount());
            tax = tax.add(f.getTaxAmount());
            total = total.add(f.getTotalAmount());
            if ("ADJUST".equals(f.getSource())) {
                adjust = adjust.add(f.getTotalAmount());
            }
        }
        s.setFeeCount(fees.size());
        s.setAmount(amount);
        s.setTaxAmount(tax);
        s.setTotalAmount(total);
        s.setAdjustAmount(adjust);
        statementMapper.updateById(s);
    }

    /** 移除某条费用（回到未对账），仅草稿/争议状态 */
    @Transactional
    public Statement removeFee(String statementNo, String feeNo) {
        Statement s = require(statementNo);
        ensureEditable(s);
        Fee f = billingService.requireFee(feeNo);
        if (!statementNo.equals(f.getStatementNo())) {
            throw new BizException("费用不属于该对账单");
        }
        if ("ADJUST".equals(f.getSource())) {
            f.setStatus(BillingService.FEE_CANCELLED);
            feeMapper.updateById(f);
        } else {
            releaseFee(f);
        }
        recalc(s);
        log(statementNo, "REMOVE_FEE", s.getStatus(), s.getStatus(), feeNo);
        return s;
    }

    /** 添加调整费用（争议处理：折让/补收），仅草稿/争议状态 */
    @Transactional
    public Statement adjust(String statementNo, ManualFeeRequest req) {
        Statement s = require(statementNo);
        ensureEditable(s);
        req.setDirection(s.getDirection());
        req.setPartnerCode(s.getPartnerCode());
        req.setStatementNo(statementNo);
        if (req.getBizDate() == null) {
            req.setBizDate(s.getPeriodEnd());
        }
        billingService.manualFee(req, "ADJUST");
        recalc(s);
        log(statementNo, "ADJUST", s.getStatus(), s.getStatus(), req.getRemark() + " " + req.getAmount());
        return s;
    }

    /** 费用退回未对账（MyBatis-Plus updateById 忽略 null，需显式 set null） */
    private void releaseFee(Fee f) {
        feeMapper.update(null, new LambdaUpdateWrapper<Fee>().eq(Fee::getId, f.getId())
                .set(Fee::getStatementNo, null).set(Fee::getStatus, BillingService.FEE_NEW));
    }

    private void ensureEditable(Statement s) {
        if (!DRAFT.equals(s.getStatus()) && !DISPUTED.equals(s.getStatus())) {
            throw new BizException("仅草稿/争议中的对账单可修改");
        }
    }

    @Transactional
    public Statement confirm(String statementNo) {
        Statement s = require(statementNo);
        ensureEditable(s);
        if (s.getTotalAmount().signum() < 0) {
            throw new BizException("对账单金额为负，不能确认");
        }
        String from = s.getStatus();
        s.setStatus(CONFIRMED);
        s.setConfirmedAt(LocalDateTime.now());
        s.setConfirmedBy(operator());
        statementMapper.updateById(s);
        statementMapper.update(null, new LambdaUpdateWrapper<Statement>()
                .eq(Statement::getId, s.getId()).set(Statement::getDisputeReason, null));
        s.setDisputeReason(null);
        log(statementNo, "CONFIRM", from, CONFIRMED, null);
        if (s.getTotalAmount().signum() == 0) {
            settle(s);
        }
        return s;
    }

    @Transactional
    public Statement dispute(String statementNo, String reason) {
        Statement s = require(statementNo);
        if (!DRAFT.equals(s.getStatus()) && !CONFIRMED.equals(s.getStatus())) {
            throw new BizException("仅草稿/已确认的对账单可标记争议");
        }
        if (CONFIRMED.equals(s.getStatus()) && (s.getPaidAmount().signum() > 0 || s.getInvoicedAmount().signum() > 0)) {
            throw new BizException("已有收付款或发票的对账单不能退回争议");
        }
        String from = s.getStatus();
        s.setStatus(DISPUTED);
        s.setDisputeReason(reason);
        statementMapper.updateById(s);
        statementMapper.update(null, new LambdaUpdateWrapper<Statement>()
                .eq(Statement::getId, s.getId()).set(Statement::getConfirmedAt, null).set(Statement::getConfirmedBy, null));
        s.setConfirmedAt(null);
        s.setConfirmedBy(null);
        log(statementNo, "DISPUTE", from, DISPUTED, reason);
        return s;
    }

    @Transactional
    public Statement cancel(String statementNo, String reason) {
        Statement s = require(statementNo);
        if (SETTLED.equals(s.getStatus()) || CANCELLED.equals(s.getStatus())) {
            throw new BizException("已结清/已作废的对账单不能作废");
        }
        if (s.getPaidAmount().signum() > 0 || s.getInvoicedAmount().signum() > 0) {
            throw new BizException("已有收付款或发票，请先作废发票/取消核销");
        }
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getStatementNo, statementNo));
        for (Fee f : fees) {
            if ("ADJUST".equals(f.getSource())) {
                f.setStatus(BillingService.FEE_CANCELLED);
                feeMapper.updateById(f);
            } else if (!BillingService.FEE_CANCELLED.equals(f.getStatus())) {
                releaseFee(f);
            }
        }
        String from = s.getStatus();
        s.setStatus(CANCELLED);
        s.setRemark(reason);
        statementMapper.updateById(s);
        log(statementNo, "CANCEL", from, CANCELLED, reason);
        return s;
    }

    private void settle(Statement s) {
        String from = s.getStatus();
        s.setStatus(SETTLED);
        statementMapper.updateById(s);
        feeMapper.update(null, new LambdaUpdateWrapper<Fee>()
                .eq(Fee::getStatementNo, s.getStatementNo()).eq(Fee::getStatus, BillingService.FEE_STATEMENTED)
                .set(Fee::getStatus, BillingService.FEE_SETTLED));
        log(s.getStatementNo(), "SETTLE", from, SETTLED, null);
    }

    // ---------------- 发票 ----------------

    @Transactional
    public Invoice issueInvoice(Invoice in) {
        Statement s = require(in.getStatementNo(), true);
        if (!CONFIRMED.equals(s.getStatus()) && !SETTLED.equals(s.getStatus())) {
            throw new BizException("仅已确认/已结清的对账单可开票");
        }
        if (in.getTotalAmount() == null || in.getTotalAmount().signum() <= 0) {
            throw new BizException("发票金额必须大于 0");
        }
        BigDecimal remain = s.getTotalAmount().subtract(s.getInvoicedAmount());
        if (in.getTotalAmount().compareTo(remain) > 0) {
            throw new BizException("开票金额超出对账单未开票金额 " + remain);
        }
        in.setId(null);
        in.setInvoiceNo(codeGenerator.next("AR".equals(s.getDirection()) ? "IV" : "IP"));
        in.setDirection(s.getDirection());
        in.setPartnerCode(s.getPartnerCode());
        if (in.getInvoiceDate() == null) {
            in.setInvoiceDate(LocalDate.now());
        }
        if (in.getInvoiceType() == null) {
            in.setInvoiceType("SPECIAL");
        }
        if (in.getAmount() == null || in.getTaxAmount() == null) {
            BigDecimal ratio = s.getTotalAmount().signum() == 0 ? BigDecimal.ZERO
                    : s.getTaxAmount().divide(s.getTotalAmount(), 6, java.math.RoundingMode.HALF_UP);
            in.setTaxAmount(in.getTotalAmount().multiply(ratio).setScale(2, java.math.RoundingMode.HALF_UP));
            in.setAmount(in.getTotalAmount().subtract(in.getTaxAmount()));
        }
        in.setStatus("ISSUED");
        invoiceMapper.insert(in);
        s.setInvoicedAmount(s.getInvoicedAmount().add(in.getTotalAmount()));
        statementMapper.updateById(s);
        log(s.getStatementNo(), "INVOICE", s.getStatus(), s.getStatus(), in.getInvoiceNo() + " " + in.getTotalAmount());
        return in;
    }

    @Transactional
    public Invoice cancelInvoice(String invoiceNo, String reason) {
        Invoice in = invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>().eq(Invoice::getInvoiceNo, invoiceNo));
        if (in == null) {
            throw new BizException("发票不存在: " + invoiceNo);
        }
        if (!"ISSUED".equals(in.getStatus())) {
            throw new BizException("发票已作废");
        }
        Statement s = require(in.getStatementNo(), true);
        int cancelled = invoiceMapper.update(null, new LambdaUpdateWrapper<Invoice>()
                .eq(Invoice::getId, in.getId()).eq(Invoice::getStatus, "ISSUED")
                .set(Invoice::getStatus, "CANCELLED").set(Invoice::getRemark, reason));
        if (cancelled == 0) {
            throw new BizException("发票已作废");
        }
        in.setStatus("CANCELLED");
        in.setRemark(reason);
        s.setInvoicedAmount(s.getInvoicedAmount().subtract(in.getTotalAmount()));
        statementMapper.updateById(s);
        log(s.getStatementNo(), "INVOICE_CANCEL", s.getStatus(), s.getStatus(), invoiceNo);
        return in;
    }

    // ---------------- 收付款与核销 ----------------

    @Transactional
    public Payment createPayment(Payment p) {
        if (!"AR".equals(p.getDirection()) && !"AP".equals(p.getDirection())) {
            throw new BizException("方向必须为 AR / AP");
        }
        if (p.getAmount() == null || p.getAmount().signum() <= 0) {
            throw new BizException("金额必须大于 0");
        }
        if (partnerMapper.selectCount(new LambdaQueryWrapper<Partner>().eq(Partner::getCode, p.getPartnerCode())) == 0) {
            throw new BizException("结算对象不存在: " + p.getPartnerCode());
        }
        p.setId(null);
        p.setPaymentNo(codeGenerator.next("AR".equals(p.getDirection()) ? "RC" : "PY"));
        p.setAppliedAmount(BigDecimal.ZERO);
        if (p.getPayDate() == null) {
            p.setPayDate(LocalDate.now());
        }
        if (p.getMethod() == null) {
            p.setMethod("TRANSFER");
        }
        paymentMapper.insert(p);
        return p;
    }

    public Payment requirePayment(String paymentNo) {
        return requirePayment(paymentNo, false);
    }

    private Payment requirePayment(String paymentNo, boolean lock) {
        LambdaQueryWrapper<Payment> q = new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo);
        if (lock) {
            q.last("for update");
        }
        Payment p = paymentMapper.selectOne(q);
        if (p == null) {
            throw new BizException("收付款单不存在: " + paymentNo);
        }
        return p;
    }

    /** 核销：把收付款分配到对账单；不传金额则自动取 min(未核销, 未收付) */
    @Transactional
    public Payment apply(PaymentApplyRequest req) {
        Payment p = requirePayment(req.getPaymentNo(), true);
        Statement s = require(req.getStatementNo(), true);
        if (!CONFIRMED.equals(s.getStatus())) {
            throw new BizException("仅已确认的对账单可核销");
        }
        if (!p.getDirection().equals(s.getDirection()) || !p.getPartnerCode().equals(s.getPartnerCode())) {
            throw new BizException("收付款单与对账单的方向/结算对象不一致");
        }
        BigDecimal unapplied = p.getAmount().subtract(p.getAppliedAmount());
        BigDecimal unpaid = s.getTotalAmount().subtract(s.getPaidAmount());
        BigDecimal amt = req.getAmount() == null ? unapplied.min(unpaid) : req.getAmount();
        if (amt.signum() <= 0) {
            throw new BizException("核销金额必须大于 0");
        }
        if (amt.compareTo(unapplied) > 0) {
            throw new BizException("核销金额超出收付款未核销金额 " + unapplied);
        }
        if (amt.compareTo(unpaid) > 0) {
            throw new BizException("核销金额超出对账单未收付金额 " + unpaid);
        }
        PaymentApply a = new PaymentApply();
        a.setPaymentNo(p.getPaymentNo());
        a.setStatementNo(s.getStatementNo());
        a.setAmount(amt);
        a.setOperator(operator());
        applyMapper.insert(a);
        p.setAppliedAmount(p.getAppliedAmount().add(amt));
        paymentMapper.updateById(p);
        s.setPaidAmount(s.getPaidAmount().add(amt));
        statementMapper.updateById(s);
        log(s.getStatementNo(), "APPLY", s.getStatus(), s.getStatus(), p.getPaymentNo() + " " + amt);
        if (s.getPaidAmount().compareTo(s.getTotalAmount()) >= 0) {
            settle(s);
        }
        return p;
    }

    @Transactional
    public Payment unapply(Long applyId) {
        PaymentApply a = applyMapper.selectById(applyId);
        if (a == null) {
            throw new BizException("核销记录不存在");
        }
        Payment p = requirePayment(a.getPaymentNo(), true);
        Statement s = require(a.getStatementNo(), true);
        if (applyMapper.deleteById(applyId) == 0) {
            throw new BizException("核销记录已取消");
        }
        p.setAppliedAmount(p.getAppliedAmount().subtract(a.getAmount()));
        paymentMapper.updateById(p);
        s.setPaidAmount(s.getPaidAmount().subtract(a.getAmount()));
        String from = s.getStatus();
        if (SETTLED.equals(s.getStatus())) {
            s.setStatus(CONFIRMED);
            feeMapper.update(null, new LambdaUpdateWrapper<Fee>()
                    .eq(Fee::getStatementNo, s.getStatementNo()).eq(Fee::getStatus, BillingService.FEE_SETTLED)
                    .set(Fee::getStatus, BillingService.FEE_STATEMENTED));
        }
        statementMapper.updateById(s);
        log(s.getStatementNo(), "UNAPPLY", from, s.getStatus(), p.getPaymentNo() + " " + a.getAmount());
        return p;
    }

    @Transactional
    public void deletePayment(String paymentNo) {
        Payment p = requirePayment(paymentNo);
        if (p.getAppliedAmount().signum() > 0) {
            throw new BizException("已核销的收付款单不能删除，请先取消核销");
        }
        paymentMapper.deleteById(p.getId());
    }
}
