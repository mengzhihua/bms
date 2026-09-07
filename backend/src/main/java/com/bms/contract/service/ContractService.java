package com.bms.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bms.basic.entity.ChargeItem;
import com.bms.basic.entity.Partner;
import com.bms.basic.mapper.ChargeItemMapper;
import com.bms.basic.mapper.PartnerMapper;
import com.bms.common.BizException;
import com.bms.contract.dto.RateRuleDetail;
import com.bms.contract.entity.Contract;
import com.bms.contract.entity.RateRule;
import com.bms.contract.entity.RateTier;
import com.bms.contract.mapper.ContractMapper;
import com.bms.contract.mapper.RateRuleMapper;
import com.bms.contract.mapper.RateTierMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {
    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String TERMINATED = "TERMINATED";
    public static final List<String> BIZ_TYPES = Arrays.asList("INBOUND", "OUTBOUND", "STORAGE", "TRANSPORT", "VAS", "RETURN");
    public static final List<String> UNITS = Arrays.asList("ORDER", "LINE", "PIECE", "BOX", "PALLET", "WEIGHT", "VOLUME", "DISTANCE",
            "PALLET_DAY", "VOLUME_DAY", "PIECE_DAY");
    public static final List<String> PRICE_MODES = Arrays.asList("FIXED", "UNIT", "TIERED", "PROGRESSIVE");

    private final ContractMapper contractMapper;
    private final RateRuleMapper ruleMapper;
    private final RateTierMapper tierMapper;
    private final PartnerMapper partnerMapper;
    private final ChargeItemMapper chargeItemMapper;

    public void validate(Contract c) {
        if (c.getContractNo() == null || c.getContractNo().trim().isEmpty()) {
            throw new BizException("合同编号不能为空");
        }
        if (!"AR".equals(c.getDirection()) && !"AP".equals(c.getDirection())) {
            throw new BizException("合同方向必须为 AR(应收) 或 AP(应付)");
        }
        Partner p = partnerMapper.selectOne(new LambdaQueryWrapper<Partner>().eq(Partner::getCode, c.getPartnerCode()));
        if (p == null) {
            throw new BizException("结算对象不存在: " + c.getPartnerCode());
        }
        if ("AR".equals(c.getDirection()) != "CUSTOMER".equals(p.getType())) {
            throw new BizException("AR 合同只能签给客户，AP 合同只能签给承运商/供应商");
        }
        if (c.getStartDate() == null || c.getEndDate() == null || c.getEndDate().isBefore(c.getStartDate())) {
            throw new BizException("合同起止日期无效");
        }
        if (c.getStatus() == null) {
            c.setStatus(DRAFT);
        }
        if (c.getTaxRate() == null) {
            c.setTaxRate(new BigDecimal("0.06"));
        }
        if (c.getCurrency() == null) {
            c.setCurrency("CNY");
        }
    }

    public Contract require(Long id) {
        Contract c = contractMapper.selectById(id);
        if (c == null) {
            throw new BizException("合同不存在");
        }
        return c;
    }

    @Transactional
    public Contract changeStatus(Long id, String to) {
        Contract c = require(id);
        String from = c.getStatus();
        if (ACTIVE.equals(to) && !(DRAFT.equals(from) || EXPIRED.equals(from))) {
            throw new BizException("仅草稿/过期合同可生效");
        }
        if (ACTIVE.equals(to) && ruleMapper.selectCount(new LambdaQueryWrapper<RateRule>().eq(RateRule::getContractId, id)) == 0) {
            throw new BizException("合同至少需要一条费率规则才能生效");
        }
        if (TERMINATED.equals(to) && !ACTIVE.equals(from)) {
            throw new BizException("仅生效中的合同可终止");
        }
        if (DRAFT.equals(to) && !TERMINATED.equals(from) && !EXPIRED.equals(from)) {
            throw new BizException("仅已终止/过期合同可退回草稿");
        }
        c.setStatus(to);
        contractMapper.updateById(c);
        return c;
    }

    @Transactional
    public RateRule saveRule(RateRuleDetail d) {
        RateRule r = d.getRule();
        Contract c = require(r.getContractId());
        if (!BIZ_TYPES.contains(r.getBizType())) {
            throw new BizException("业务类型无效: " + r.getBizType());
        }
        if (!UNITS.contains(r.getUnit())) {
            throw new BizException("计费单位无效: " + r.getUnit());
        }
        if (!PRICE_MODES.contains(r.getPriceMode())) {
            throw new BizException("计价方式无效: " + r.getPriceMode());
        }
        ChargeItem item = chargeItemMapper.selectOne(new LambdaQueryWrapper<ChargeItem>().eq(ChargeItem::getCode, r.getChargeItemCode()));
        if (item == null) {
            throw new BizException("费用项目不存在: " + r.getChargeItemCode());
        }
        if (!"BOTH".equals(item.getDirection()) && !c.getDirection().equals(item.getDirection())) {
            throw new BizException("费用项目 " + item.getName() + " 不适用于 " + c.getDirection() + " 合同");
        }
        if (r.getWarehouseCode() != null && r.getWarehouseCode().trim().isEmpty()) {
            r.setWarehouseCode(null);
        }
        boolean tiered = "TIERED".equals(r.getPriceMode()) || "PROGRESSIVE".equals(r.getPriceMode());
        List<RateTier> tiers = d.getTiers();
        if (tiered) {
            if (tiers == null || tiers.isEmpty()) {
                throw new BizException("阶梯/累进计价必须配置阶梯区间");
            }
            tiers.sort(Comparator.comparing(RateTier::getFromQty));
            BigDecimal prev = null;
            for (RateTier t : tiers) {
                if (t.getFromQty() == null || t.getPrice() == null) {
                    throw new BizException("阶梯区间下限与价格不能为空");
                }
                if (prev != null && t.getFromQty().compareTo(prev) != 0) {
                    throw new BizException("阶梯区间必须连续（上一区间上限 = 下一区间下限）");
                }
                if (t.getToQty() != null && t.getToQty().compareTo(t.getFromQty()) <= 0) {
                    throw new BizException("阶梯区间上限必须大于下限");
                }
                prev = t.getToQty();
            }
        }
        if (r.getUnitPrice() == null) {
            r.setUnitPrice(BigDecimal.ZERO);
        }
        if (r.getMinCharge() == null) {
            r.setMinCharge(BigDecimal.ZERO);
        }
        if (r.getStatus() == null) {
            r.setStatus(1);
        }
        if (r.getPriority() == null) {
            r.setPriority(0);
        }
        if (r.getId() == null) {
            ruleMapper.insert(r);
        } else {
            ruleMapper.updateById(r);
            tierMapper.delete(new LambdaQueryWrapper<RateTier>().eq(RateTier::getRuleId, r.getId()));
        }
        if (tiered) {
            for (RateTier t : tiers) {
                t.setId(null);
                t.setRuleId(r.getId());
                tierMapper.insert(t);
            }
        }
        return r;
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        ruleMapper.deleteById(ruleId);
        tierMapper.delete(new LambdaQueryWrapper<RateTier>().eq(RateTier::getRuleId, ruleId));
    }

    public List<RateRuleDetail> rules(Long contractId) {
        List<RateRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<RateRule>()
                .eq(RateRule::getContractId, contractId).orderByAsc(RateRule::getBizType).orderByDesc(RateRule::getPriority));
        if (rules.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Long> ids = rules.stream().map(RateRule::getId).collect(Collectors.toList());
        Map<Long, List<RateTier>> tiers = tierMapper.selectList(new LambdaQueryWrapper<RateTier>().in(RateTier::getRuleId, ids)
                        .orderByAsc(RateTier::getFromQty))
                .stream().collect(Collectors.groupingBy(RateTier::getRuleId));
        return rules.stream().map(r -> new RateRuleDetail(r, tiers.getOrDefault(r.getId(), java.util.Collections.emptyList())))
                .collect(Collectors.toList());
    }

    /** 某结算对象在某日期生效的合同（含规则），供计费引擎使用 */
    public List<Contract> effectiveContracts(String direction, String partnerCode, LocalDate date) {
        return contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getDirection, direction)
                .eq(Contract::getPartnerCode, partnerCode)
                .eq(Contract::getStatus, ACTIVE)
                .le(Contract::getStartDate, date)
                .ge(Contract::getEndDate, date));
    }

    /** 到期自动标记过期 */
    @Transactional
    public int expireOutdated() {
        List<Contract> list = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, ACTIVE).lt(Contract::getEndDate, LocalDate.now()));
        for (Contract c : list) {
            c.setStatus(EXPIRED);
            contractMapper.updateById(c);
        }
        return list.size();
    }
}
