package com.bms.contract.controller;

import com.bms.common.BaseCrudController;
import com.bms.common.BizException;
import com.bms.common.R;
import com.bms.contract.dto.RateRuleDetail;
import com.bms.contract.entity.Contract;
import com.bms.contract.entity.RateRule;
import com.bms.contract.mapper.ContractMapper;
import com.bms.contract.service.ContractService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contract")
public class ContractController extends BaseCrudController<Contract, ContractMapper> {
    private final ContractService service;

    public ContractController(ContractService service) {
        super(Contract.class);
        this.service = service;
    }

    @Override
    protected String[] keywordColumns() {
        return new String[]{"contract_no", "name", "partner_code"};
    }

    @Override
    protected void beforeSave(Contract entity) {
        service.validate(entity);
    }

    @Override
    public R<Contract> update(@PathVariable Long id, @RequestBody Contract entity) {
        Contract old = service.require(id);
        if (entity.getStatus() == null) {
            entity.setStatus(old.getStatus());
        }
        return super.update(id, entity);
    }

    @Override
    public R<Void> delete(@PathVariable Long id) {
        Contract c = service.require(id);
        if (ContractService.ACTIVE.equals(c.getStatus())) {
            throw new BizException("生效中的合同不能删除，请先终止");
        }
        for (RateRuleDetail d : service.rules(id)) {
            service.deleteRule(d.getRule().getId());
        }
        return super.delete(id);
    }

    @PostMapping("/{id}/activate")
    public R<Contract> activate(@PathVariable Long id) {
        return R.ok(service.changeStatus(id, ContractService.ACTIVE));
    }

    @PostMapping("/{id}/terminate")
    public R<Contract> terminate(@PathVariable Long id) {
        return R.ok(service.changeStatus(id, ContractService.TERMINATED));
    }

    @PostMapping("/{id}/reopen")
    public R<Contract> reopen(@PathVariable Long id) {
        return R.ok(service.changeStatus(id, ContractService.DRAFT));
    }

    @GetMapping("/{id}/rules")
    public R<List<RateRuleDetail>> rules(@PathVariable Long id) {
        return R.ok(service.rules(id));
    }

    @PostMapping("/{id}/rules")
    public R<RateRule> saveRule(@PathVariable Long id, @RequestBody RateRuleDetail detail) {
        if (detail.getRule() == null) {
            throw new BizException("规则内容不能为空");
        }
        detail.getRule().setContractId(id);
        return R.ok(service.saveRule(detail));
    }

    @DeleteMapping("/{id}/rules/{ruleId}")
    public R<Void> deleteRule(@PathVariable Long id, @PathVariable Long ruleId) {
        service.deleteRule(ruleId);
        return R.ok();
    }

    @PostMapping("/expire")
    public R<Integer> expire() {
        return R.ok(service.expireOutdated());
    }
}
