package com.bms.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bms.basic.entity.ChargeItem;
import com.bms.basic.entity.Partner;
import com.bms.basic.entity.Warehouse;
import com.bms.basic.mapper.ChargeItemMapper;
import com.bms.basic.mapper.PartnerMapper;
import com.bms.basic.mapper.WarehouseMapper;
import com.bms.billing.dto.ManualFeeRequest;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.BizDocMapper;
import com.bms.billing.mapper.FeeMapper;
import com.bms.common.BizException;
import com.bms.common.CodeGenerator;
import com.bms.contract.dto.RateRuleDetail;
import com.bms.contract.entity.Contract;
import com.bms.contract.entity.RateRule;
import com.bms.contract.service.ContractService;
import com.bms.system.auth.CurrentUser;
import com.bms.system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {
    public static final String PENDING = "PENDING";
    public static final String BILLED = "BILLED";
    public static final String FAILED = "FAILED";
    public static final String IGNORED = "IGNORED";

    public static final String FEE_NEW = "NEW";
    public static final String FEE_STATEMENTED = "STATEMENTED";
    public static final String FEE_SETTLED = "SETTLED";
    public static final String FEE_CANCELLED = "CANCELLED";

    private final BizDocMapper docMapper;
    private final FeeMapper feeMapper;
    private final PartnerMapper partnerMapper;
    private final WarehouseMapper warehouseMapper;
    private final ChargeItemMapper chargeItemMapper;
    private final ContractService contractService;
    private final CodeGenerator codeGenerator;

    // ---------------- 业务单据 ----------------

    /** 新建业务单据；source+extRef 已存在则直接返回已存在单据（幂等） */
    @Transactional
    public BizDoc createDoc(BizDoc d, boolean autoBill) {
        if (d.getSource() == null || d.getSource().isEmpty()) {
            d.setSource("MANUAL");
        }
        if (d.getExtRef() != null && !d.getExtRef().isEmpty()) {
            BizDoc exist = docMapper.selectOne(new LambdaQueryWrapper<BizDoc>()
                    .eq(BizDoc::getSource, d.getSource()).eq(BizDoc::getExtRef, d.getExtRef()).last("limit 1"));
            if (exist != null) {
                return exist;
            }
        }
        if (!ContractService.BIZ_TYPES.contains(d.getBizType())) {
            throw new BizException("业务类型无效: " + d.getBizType());
        }
        requirePartner(d.getCustomerCode(), "CUSTOMER");
        if (d.getSupplierCode() != null && d.getSupplierCode().trim().isEmpty()) {
            d.setSupplierCode(null);
        }
        if (d.getSupplierCode() != null) {
            Partner s = requirePartner(d.getSupplierCode(), null);
            if ("CUSTOMER".equals(s.getType())) {
                throw new BizException("应付对象必须是承运商/供应商: " + d.getSupplierCode());
            }
        }
        if (d.getWarehouseCode() != null && !d.getWarehouseCode().isEmpty()
                && warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getCode, d.getWarehouseCode())) == 0) {
            throw new BizException("仓库不存在: " + d.getWarehouseCode());
        }
        if (d.getBizDate() == null) {
            d.setBizDate(LocalDate.now());
        }
        d.setId(null);
        d.setDocNo(codeGenerator.next("BD"));
        d.setBillStatus(PENDING);
        d.setFailReason(null);
        d.setArAmount(BigDecimal.ZERO);
        d.setApAmount(BigDecimal.ZERO);
        if (d.getOrders() == null) {
            d.setOrders(1);
        }
        if (d.getDays() == null) {
            d.setDays(1);
        }
        try {
            docMapper.insert(d);
        } catch (DuplicateKeyException e) {
            BizDoc exist = docMapper.selectOne(new LambdaQueryWrapper<BizDoc>()
                    .eq(BizDoc::getSource, d.getSource()).eq(BizDoc::getExtRef, d.getExtRef()).last("limit 1 for update"));
            if (exist == null) {
                throw e;
            }
            return exist;
        }
        if (autoBill) {
            bill(d.getDocNo());
        }
        return docMapper.selectById(d.getId());
    }

    /** 按调用方给出的金额记一笔费用，不再走合同费率。税额按 0，金额与差额单一致。 */
    @Transactional
    public BizDoc bookStated(BizDoc doc, String direction, String chargeItem, BigDecimal amount, String detail) {
        if (doc == null) {
            throw new BizException("业务单据不存在");
        }
        if (amount == null) {
            throw new BizException("声明金额不能为空");
        }
        String dir = "AR".equalsIgnoreCase(direction) ? "AR" : "AP";
        String partner = "AR".equals(dir) ? doc.getCustomerCode() : doc.getSupplierCode();
        if (partner == null || partner.trim().isEmpty()) {
            throw new BizException(dir + " 结算对象不能为空");
        }
        Fee fee = new Fee();
        fee.setDirection(dir);
        fee.setPartnerCode(partner);
        fee.setDocNo(doc.getDocNo());
        fee.setBizType(doc.getBizType());
        fee.setWarehouseCode(doc.getWarehouseCode());
        fee.setChargeItemCode(chargeItem == null || chargeItem.trim().isEmpty() ? "FREIGHT" : chargeItem.trim());
        fee.setBizDate(doc.getBizDate());
        fee.setUnit("BILL");
        fee.setQty(BigDecimal.ONE);
        fee.setUnitPrice(amount);
        fee.setSource("STATED");
        fee.setCalcDetail(detail);
        insertFee(fee, amount, BigDecimal.ZERO);
        if ("AR".equals(dir)) {
            doc.setArAmount(fee.getTotalAmount());
        } else {
            doc.setApAmount(fee.getTotalAmount());
        }
        doc.setBillStatus(BILLED);
        doc.setFailReason(null);
        docMapper.updateById(doc);
        return doc;
    }

    public BizDoc findByRef(String source, String extRef) {
        return docMapper.selectOne(new LambdaQueryWrapper<BizDoc>()
                .eq(BizDoc::getSource, source).eq(BizDoc::getExtRef, extRef).last("limit 1"));
    }

    public BizDoc requireDoc(String docNo) {
        BizDoc d = docMapper.selectOne(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getDocNo, docNo));
        if (d == null) {
            throw new BizException("业务单据不存在: " + docNo);
        }
        return d;
    }

    private Partner requirePartner(String code, String type) {
        if (code == null || code.isEmpty()) {
            throw new BizException("结算对象编码不能为空");
        }
        Partner p = partnerMapper.selectOne(new LambdaQueryWrapper<Partner>().eq(Partner::getCode, code));
        if (p == null) {
            throw new BizException("结算对象不存在: " + code);
        }
        if (type != null && !type.equals(p.getType())) {
            throw new BizException("结算对象 " + code + " 不是" + ("CUSTOMER".equals(type) ? "客户" : type));
        }
        return p;
    }

    /** 对单据自动计费（可重算）：删除旧的未对账自动费用，按生效合同规则重新生成 */
    @Transactional
    public BizDoc bill(String docNo) {
        BizDoc d = requireDoc(docNo);
        if (IGNORED.equals(d.getBillStatus())) {
            throw new BizException("单据已忽略，不能计费");
        }
        List<Fee> old = feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, docNo).eq(Fee::getSource, "AUTO"));
        for (Fee f : old) {
            if (!FEE_NEW.equals(f.getStatus()) && !FEE_CANCELLED.equals(f.getStatus())) {
                throw new BizException("单据已有费用进入对账/结算，不能重算");
            }
        }
        for (Fee f : old) {
            feeMapper.deleteById(f.getId());
        }
        List<String> problems = new ArrayList<>();
        BigDecimal ar = rateDirection("AR", d.getCustomerCode(), d, problems);
        BigDecimal ap = d.getSupplierCode() == null ? BigDecimal.ZERO : rateDirection("AP", d.getSupplierCode(), d, problems);
        d.setArAmount(ar);
        d.setApAmount(ap);
        if (!problems.isEmpty()) {
            feeMapper.delete(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, docNo).eq(Fee::getSource, "AUTO"));
            d.setArAmount(BigDecimal.ZERO);
            d.setApAmount(BigDecimal.ZERO);
            d.setBillStatus(FAILED);
            d.setFailReason(String.join("; ", problems));
        } else {
            d.setBillStatus(BILLED);
            d.setFailReason(null);
        }
        docMapper.updateById(d);
        docMapper.update(null, new LambdaUpdateWrapper<BizDoc>().eq(BizDoc::getId, d.getId()).set(BizDoc::getFailReason, d.getFailReason()));
        return d;
    }

    private BigDecimal rateDirection(String direction, String partnerCode, BizDoc d, List<String> problems) {
        List<Contract> contracts = contractService.effectiveContracts(direction, partnerCode, d.getBizDate());
        if (contracts.isEmpty()) {
            problems.add(direction + " 无生效合同(" + partnerCode + ")");
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        int matched = 0;
        for (Contract c : contracts) {
            for (RateRuleDetail rd : selectRules(contractService.rules(c.getId()), d)) {
                matched++;
                try {
                    RatingEngine.Result r = RatingEngine.rate(rd.getRule(), rd.getTiers(), d);
                    Fee f = new Fee();
                    f.setDirection(direction);
                    f.setPartnerCode(partnerCode);
                    f.setContractNo(c.getContractNo());
                    f.setRuleId(rd.getRule().getId());
                    f.setDocNo(d.getDocNo());
                    f.setBizType(d.getBizType());
                    f.setWarehouseCode(d.getWarehouseCode());
                    f.setChargeItemCode(rd.getRule().getChargeItemCode());
                    f.setBizDate(d.getBizDate());
                    f.setUnit(rd.getRule().getUnit());
                    f.setQty(r.getQty());
                    f.setUnitPrice(r.getUnitPrice());
                    f.setSource("AUTO");
                    f.setCalcDetail(r.getDetail());
                    f.setRemark(rd.getRule().getRemark());
                    insertFee(f, r.getAmount(), c.getTaxRate());
                    total = total.add(f.getTotalAmount());
                } catch (BizException e) {
                    problems.add(direction + " " + rd.getRule().getChargeItemCode() + ": " + e.getMessage());
                }
            }
        }
        if (matched == 0) {
            problems.add(direction + " 合同无匹配 " + d.getBizType() + " 费率(" + partnerCode + ")");
        }
        return total;
    }

    /** 同一合同内，每个费用项目只取一条最优规则：优先级高者优先，同优先级时仓库专用规则优先于通用规则 */
    public static List<RateRuleDetail> selectRules(List<RateRuleDetail> rules, BizDoc d) {
        Map<String, RateRuleDetail> best = new LinkedHashMap<>();
        for (RateRuleDetail rd : rules) {
            RateRule r = rd.getRule();
            if (r.getStatus() == null || r.getStatus() != 1 || !r.getBizType().equals(d.getBizType())) {
                continue;
            }
            if (r.getWarehouseCode() != null && !r.getWarehouseCode().equals(d.getWarehouseCode())) {
                continue;
            }
            RateRuleDetail cur = best.get(r.getChargeItemCode());
            if (cur == null || better(r, cur.getRule())) {
                best.put(r.getChargeItemCode(), rd);
            }
        }
        return new ArrayList<>(best.values());
    }

    private static boolean better(RateRule a, RateRule b) {
        int pa = a.getPriority() == null ? 0 : a.getPriority();
        int pb = b.getPriority() == null ? 0 : b.getPriority();
        if (pa != pb) {
            return pa > pb;
        }
        return a.getWarehouseCode() != null && b.getWarehouseCode() == null;
    }

    private void insertFee(Fee f, BigDecimal amount, BigDecimal taxRate) {
        BigDecimal rate = taxRate == null ? BigDecimal.ZERO : taxRate;
        f.setId(null);
        f.setFeeNo(codeGenerator.next("FE"));
        f.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        f.setTaxRate(rate);
        f.setTaxAmount(f.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        f.setTotalAmount(f.getAmount().add(f.getTaxAmount()));
        f.setStatus(FEE_NEW);
        f.setStatementNo(null);
        User u = CurrentUser.get();
        f.setCreatedBy(u == null ? "system" : u.getUsername());
        feeMapper.insert(f);
    }

    /** 批量计费所有待计费 / 失败单据 */
    @Transactional
    public int billPending() {
        List<BizDoc> list = docMapper.selectList(new LambdaQueryWrapper<BizDoc>().in(BizDoc::getBillStatus, PENDING, FAILED));
        int ok = 0;
        for (BizDoc d : list) {
            if (BILLED.equals(bill(d.getDocNo()).getBillStatus())) {
                ok++;
            }
        }
        return ok;
    }

    @Transactional
    public BizDoc ignore(String docNo, String reason) {
        BizDoc d = requireDoc(docNo);
        if (BILLED.equals(d.getBillStatus())) {
            throw new BizException("已计费单据不能忽略，请先作废费用");
        }
        d.setBillStatus(IGNORED);
        d.setFailReason(reason);
        docMapper.updateById(d);
        return d;
    }

    @Transactional
    public BizDoc reopen(String docNo) {
        BizDoc d = requireDoc(docNo);
        if (!IGNORED.equals(d.getBillStatus())) {
            throw new BizException("仅已忽略单据可恢复");
        }
        d.setBillStatus(PENDING);
        d.setFailReason(null);
        docMapper.update(null, new LambdaUpdateWrapper<BizDoc>().eq(BizDoc::getId, d.getId())
                .set(BizDoc::getBillStatus, PENDING).set(BizDoc::getFailReason, null));
        return d;
    }

    // ---------------- 费用 ----------------

    public Fee requireFee(String feeNo) {
        Fee f = feeMapper.selectOne(new LambdaQueryWrapper<Fee>().eq(Fee::getFeeNo, feeNo));
        if (f == null) {
            throw new BizException("费用不存在: " + feeNo);
        }
        return f;
    }

    /** 手工费用 / 调整费用（可为负数，用于冲减） */
    @Transactional
    public Fee manualFee(ManualFeeRequest req, String source) {
        if (!"AR".equals(req.getDirection()) && !"AP".equals(req.getDirection())) {
            throw new BizException("方向必须为 AR / AP");
        }
        Partner p = requirePartner(req.getPartnerCode(), null);
        if ("AR".equals(req.getDirection()) != "CUSTOMER".equals(p.getType())) {
            throw new BizException("AR 费用对象须为客户，AP 费用对象须为承运商/供应商");
        }
        ChargeItem item = chargeItemMapper.selectOne(new LambdaQueryWrapper<ChargeItem>().eq(ChargeItem::getCode, req.getChargeItemCode()));
        if (item == null) {
            throw new BizException("费用项目不存在: " + req.getChargeItemCode());
        }
        if (req.getAmount() == null || req.getAmount().signum() == 0) {
            throw new BizException("金额不能为 0");
        }
        Fee f = new Fee();
        f.setDirection(req.getDirection());
        f.setPartnerCode(req.getPartnerCode());
        f.setChargeItemCode(req.getChargeItemCode());
        f.setBizDate(req.getBizDate() == null ? LocalDate.now() : req.getBizDate());
        f.setDocNo(req.getDocNo());
        f.setWarehouseCode(req.getWarehouseCode());
        f.setBizType(req.getBizType());
        f.setQty(req.getQty() == null ? BigDecimal.ONE : req.getQty());
        f.setUnitPrice(f.getQty().signum() == 0 ? BigDecimal.ZERO : req.getAmount().divide(f.getQty(), 4, RoundingMode.HALF_UP));
        f.setUnit(req.getUnit());
        f.setSource(source);
        f.setRemark(req.getRemark());
        f.setCalcDetail("MANUAL".equals(source) ? "手工录入" : "调整: " + req.getRemark());
        BigDecimal rate = req.getTaxRate() != null ? req.getTaxRate() : item.getDefaultTaxRate();
        if (req.getDocNo() != null && !req.getDocNo().isEmpty()) {
            requireDoc(req.getDocNo());
        }
        insertFee(f, req.getAmount(), rate);
        if (req.getStatementNo() != null && !req.getStatementNo().isEmpty()) {
            f.setStatementNo(req.getStatementNo());
            f.setStatus(FEE_STATEMENTED);
            feeMapper.updateById(f);
        }
        return f;
    }

    @Transactional
    public Fee cancelFee(String feeNo, String reason) {
        Fee f = requireFee(feeNo);
        if (!FEE_NEW.equals(f.getStatus())) {
            throw new BizException("仅未对账费用可作废");
        }
        f.setStatus(FEE_CANCELLED);
        f.setRemark(reason);
        feeMapper.updateById(f);
        if (f.getDocNo() != null && "AUTO".equals(f.getSource())) {
            BizDoc d = docMapper.selectOne(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getDocNo, f.getDocNo()));
            if (d != null) {
                if ("AR".equals(f.getDirection())) {
                    d.setArAmount(nz(d.getArAmount()).subtract(f.getTotalAmount()));
                } else {
                    d.setApAmount(nz(d.getApAmount()).subtract(f.getTotalAmount()));
                }
                docMapper.updateById(d);
            }
        }
        return f;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
