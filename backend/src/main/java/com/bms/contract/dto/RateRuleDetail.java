package com.bms.contract.dto;

import com.bms.contract.entity.RateRule;
import com.bms.contract.entity.RateTier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateRuleDetail {
    private RateRule rule;
    private List<RateTier> tiers;
}
