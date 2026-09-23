package com.bms.billing.service;

import com.bms.billing.entity.BizDoc;
import com.bms.contract.entity.RateRule;
import com.bms.contract.entity.RateTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RatingEngineTest {
    @Test
    void firstExtraChargesIncludedWeightThenUnitPrice() {
        RateRule rule = new RateRule();
        rule.setUnit("WEIGHT");
        rule.setPriceMode("FIRST_EXTRA");
        rule.setUnitPrice(new BigDecimal("2"));
        RateTier first = new RateTier();
        first.setFromQty(BigDecimal.ZERO);
        first.setToQty(new BigDecimal("1"));
        first.setPrice(new BigDecimal("12"));
        BizDoc doc = new BizDoc();
        doc.setWeight(new BigDecimal("3.5"));
        RatingEngine.Result result = RatingEngine.rate(rule, Collections.singletonList(first), doc);
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("17.00")));
        assertEquals("首重 1 12 元，超出 2.5 × 2", result.getDetail());
    }

    @Test
    void weightInsideTheFirstBandDoesNotAddExtra() {
        RateRule rule = new RateRule();
        rule.setUnit("WEIGHT");
        rule.setPriceMode("FIRST_EXTRA");
        rule.setUnitPrice(new BigDecimal("2"));
        RateTier first = new RateTier();
        first.setFromQty(BigDecimal.ZERO);
        first.setToQty(new BigDecimal("1"));
        first.setPrice(new BigDecimal("12"));
        BizDoc doc = new BizDoc();
        doc.setWeight(new BigDecimal("0.4"));
        RatingEngine.Result result = RatingEngine.rate(rule, Collections.singletonList(first), doc);
        assertEquals(0, result.getAmount().compareTo(new BigDecimal("12.00")));
    }
}
