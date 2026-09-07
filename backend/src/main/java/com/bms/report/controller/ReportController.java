package com.bms.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.mapper.BizDocMapper;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.R;
import com.bms.contract.entity.Contract;
import com.bms.contract.mapper.ContractMapper;
import com.bms.settlement.entity.Statement;
import com.bms.settlement.mapper.StatementMapper;
import com.bms.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {
    private final FeeMapper feeMapper;
    private final BizDocMapper docMapper;
    private final StatementMapper statementMapper;
    private final ContractMapper contractMapper;

    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1) : from;
        return new LocalDate[]{f, t};
    }

    /** 首页看板 */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingDocs", docMapper.selectCount(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getBillStatus, BillingService.PENDING)));
        m.put("failedDocs", docMapper.selectCount(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getBillStatus, BillingService.FAILED)));
        m.put("monthAr", sum(feeMapper.summary("AR", null, null, monthStart, today)));
        m.put("monthAp", sum(feeMapper.summary("AP", null, null, monthStart, today)));
        m.put("unstatementedAr", sum(feeMapper.summary("AR", null, BillingService.FEE_NEW, null, null)));
        m.put("unstatementedAp", sum(feeMapper.summary("AP", null, BillingService.FEE_NEW, null, null)));
        m.put("draftStatements", statementMapper.selectCount(new LambdaQueryWrapper<Statement>().eq(Statement::getStatus, SettlementService.DRAFT)));
        m.put("disputedStatements", statementMapper.selectCount(new LambdaQueryWrapper<Statement>().eq(Statement::getStatus, SettlementService.DISPUTED)));
        m.put("arOutstanding", outstanding("AR"));
        m.put("apOutstanding", outstanding("AP"));
        m.put("overdueStatements", statementMapper.selectCount(new LambdaQueryWrapper<Statement>()
                .eq(Statement::getStatus, SettlementService.CONFIRMED).lt(Statement::getDueDate, today)));
        m.put("expiringContracts", contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, "ACTIVE").between(Contract::getEndDate, today, today.plusDays(30))));
        m.put("trend", feeMapper.dailyTrend(today.minusDays(29), today));
        m.put("topPartners", feeMapper.groupByPartner(monthStart, today));
        return R.ok(m);
    }

    private static BigDecimal sum(Map<String, Object> row) {
        Object v = row == null ? null : row.get("totalAmount");
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private BigDecimal outstanding(String direction) {
        BigDecimal total = BigDecimal.ZERO;
        for (Statement s : statementMapper.selectList(new LambdaQueryWrapper<Statement>()
                .eq(Statement::getDirection, direction).eq(Statement::getStatus, SettlementService.CONFIRMED))) {
            total = total.add(s.getTotalAmount().subtract(s.getPaidAmount()));
        }
        return total;
    }

    /** 费用汇总：按结算对象 × 费用项目 */
    @GetMapping("/fee-summary")
    public R<List<Map<String, Object>>> feeSummary(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        LocalDate[] r = range(from, to);
        return R.ok(feeMapper.groupByPartnerItem(r[0], r[1]));
    }

    /** 费用项目构成 */
    @GetMapping("/fee-by-item")
    public R<List<Map<String, Object>>> feeByItem(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        LocalDate[] r = range(from, to);
        return R.ok(feeMapper.groupByItem(r[0], r[1]));
    }

    /** 收入/成本/毛利（按结算对象；AP 无法直接归属客户时按整体列出） */
    @GetMapping("/profit")
    public R<Map<String, Object>> profit(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        LocalDate[] r = range(from, to);
        List<Map<String, Object>> rows = feeMapper.groupByPartner(r[0], r[1]);
        BigDecimal ar = BigDecimal.ZERO;
        BigDecimal ap = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal amt = new BigDecimal(String.valueOf(row.get("totalAmount")));
            if ("AR".equals(String.valueOf(row.get("direction")))) {
                ar = ar.add(amt);
            } else {
                ap = ap.add(amt);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", r[0]);
        m.put("to", r[1]);
        m.put("revenue", ar);
        m.put("cost", ap);
        m.put("grossProfit", ar.subtract(ap));
        m.put("grossMargin", ar.signum() == 0 ? BigDecimal.ZERO : ar.subtract(ap).multiply(BigDecimal.valueOf(100)).divide(ar, 2, java.math.RoundingMode.HALF_UP));
        m.put("rows", rows);
        m.put("trend", feeMapper.dailyTrend(r[0], r[1]));
        return R.ok(m);
    }

    /** 账龄分析：已确认未结清对账单按逾期天数分桶 */
    @GetMapping("/aging")
    public R<List<Map<String, Object>>> aging(@RequestParam(defaultValue = "AR") String direction) {
        LocalDate today = LocalDate.now();
        List<Statement> list = statementMapper.selectList(new LambdaQueryWrapper<Statement>()
                .eq(Statement::getDirection, direction).eq(Statement::getStatus, SettlementService.CONFIRMED));
        Map<String, Map<String, Object>> byPartner = new LinkedHashMap<>();
        for (Statement s : list) {
            Map<String, Object> row = byPartner.computeIfAbsent(s.getPartnerCode(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("partnerCode", k);
                m.put("notDue", BigDecimal.ZERO);
                m.put("d1_30", BigDecimal.ZERO);
                m.put("d31_60", BigDecimal.ZERO);
                m.put("d61_90", BigDecimal.ZERO);
                m.put("d90p", BigDecimal.ZERO);
                m.put("total", BigDecimal.ZERO);
                m.put("count", 0);
                return m;
            });
            BigDecimal remain = s.getTotalAmount().subtract(s.getPaidAmount());
            long overdue = s.getDueDate() == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(s.getDueDate(), today);
            String bucket = overdue <= 0 ? "notDue" : overdue <= 30 ? "d1_30" : overdue <= 60 ? "d31_60" : overdue <= 90 ? "d61_90" : "d90p";
            row.put(bucket, ((BigDecimal) row.get(bucket)).add(remain));
            row.put("total", ((BigDecimal) row.get("total")).add(remain));
            row.put("count", (Integer) row.get("count") + 1);
        }
        return R.ok(new ArrayList<>(byPartner.values()));
    }

    /** 结算对象对账台账 */
    @GetMapping("/partner-ledger")
    public R<List<Statement>> ledger(@RequestParam String partnerCode, @RequestParam(required = false) String direction) {
        return R.ok(statementMapper.selectList(new LambdaQueryWrapper<Statement>()
                .eq(Statement::getPartnerCode, partnerCode)
                .eq(direction != null && !direction.isEmpty(), Statement::getDirection, direction)
                .ne(Statement::getStatus, SettlementService.CANCELLED)
                .orderByDesc(Statement::getPeriodStart)));
    }
}
