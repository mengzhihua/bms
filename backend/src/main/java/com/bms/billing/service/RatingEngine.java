package com.bms.billing.service;

import com.bms.billing.entity.BizDoc;
import com.bms.common.BizException;
import com.bms.contract.entity.RateRule;
import com.bms.contract.entity.RateTier;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 纯计算：根据费率规则与业务单据量值算出金额（不含税），并给出计算过程说明 */
public final class RatingEngine {
    private RatingEngine() {
    }

    @Data
    public static class Result {
        private BigDecimal qty;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String detail;
    }

    /** 按规则的计费单位从单据取量 */
    public static BigDecimal quantity(String unit, BizDoc d) {
        BigDecimal days = BigDecimal.valueOf(nz(d.getDays()) == 0 ? 1 : d.getDays());
        switch (unit) {
            case "ORDER":
                return BigDecimal.valueOf(nz(d.getOrders()) == 0 ? 1 : d.getOrders());
            case "LINE":
                return BigDecimal.valueOf(nz(d.getLines()));
            case "PIECE":
                return nz(d.getQty());
            case "BOX":
                return nz(d.getBoxes());
            case "PALLET":
                return nz(d.getPallets());
            case "WEIGHT":
                return nz(d.getWeight());
            case "VOLUME":
                return nz(d.getVolume());
            case "DISTANCE":
                return nz(d.getDistance());
            case "PALLET_DAY":
                return nz(d.getPallets()).multiply(days);
            case "VOLUME_DAY":
                return nz(d.getVolume()).multiply(days);
            case "PIECE_DAY":
                return nz(d.getQty()).multiply(days);
            default:
                throw new BizException("未知计费单位: " + unit);
        }
    }

    public static Result rate(RateRule rule, List<RateTier> tiers, BizDoc doc) {
        BigDecimal qty = quantity(rule.getUnit(), doc);
        Result r = new Result();
        r.setQty(qty);
        BigDecimal amount;
        StringBuilder detail = new StringBuilder();
        switch (rule.getPriceMode()) {
            case "FIXED":
                amount = nz(rule.getUnitPrice());
                r.setUnitPrice(amount);
                detail.append("固定 ").append(plain(amount));
                break;
            case "UNIT":
                amount = qty.multiply(nz(rule.getUnitPrice()));
                r.setUnitPrice(nz(rule.getUnitPrice()));
                detail.append(plain(qty)).append(" × ").append(plain(rule.getUnitPrice()));
                break;
            case "TIERED": {
                RateTier t = find(tiers, qty);
                amount = qty.multiply(t.getPrice());
                r.setUnitPrice(t.getPrice());
                detail.append("阶梯(").append(plain(t.getFromQty())).append("-").append(t.getToQty() == null ? "∞" : plain(t.getToQty()))
                        .append(") ").append(plain(qty)).append(" × ").append(plain(t.getPrice()));
                break;
            }
            case "PROGRESSIVE": {
                amount = BigDecimal.ZERO;
                boolean first = true;
                for (RateTier t : tiers) {
                    if (qty.compareTo(t.getFromQty()) <= 0) {
                        break;
                    }
                    BigDecimal upper = t.getToQty() == null ? qty : qty.min(t.getToQty());
                    BigDecimal part = upper.subtract(t.getFromQty());
                    BigDecimal sub;
                    if (first && t.getToQty() != null && t.getFromQty().signum() == 0 && part.compareTo(t.getToQty()) <= 0) {
                        // 首段视为“首重/首件”整段价
                        sub = t.getPrice();
                        detail.append("首段 ").append(plain(t.getPrice()));
                    } else {
                        sub = part.multiply(t.getPrice());
                        detail.append(" + ").append(plain(part)).append(" × ").append(plain(t.getPrice()));
                    }
                    first = false;
                    amount = amount.add(sub);
                }
                r.setUnitPrice(qty.signum() == 0 ? BigDecimal.ZERO : amount.divide(qty, 4, RoundingMode.HALF_UP));
                break;
            }
            case "FIRST_EXTRA": {
                if (tiers == null || tiers.isEmpty() || tiers.get(0).getToQty() == null) {
                    throw new BizException("首重续重需要首重数量和首重金额");
                }
                RateTier first = tiers.get(0);
                BigDecimal extraQty = qty.subtract(first.getToQty());
                if (extraQty.signum() < 0) {
                    extraQty = BigDecimal.ZERO;
                }
                BigDecimal extraPrice = nz(rule.getUnitPrice());
                amount = nz(first.getPrice()).add(extraQty.multiply(extraPrice));
                r.setUnitPrice(extraPrice);
                detail.append("首重 ").append(plain(first.getToQty())).append(" ").append(plain(first.getPrice()))
                        .append(" 元，超出 ").append(plain(extraQty)).append(" × ").append(plain(extraPrice));
                break;
            }
            default:
                throw new BizException("未知计价方式: " + rule.getPriceMode());
        }
        if (rule.getMinCharge() != null && amount.compareTo(rule.getMinCharge()) < 0) {
            detail.append(" → 最低收费 ").append(plain(rule.getMinCharge()));
            amount = rule.getMinCharge();
        }
        if (rule.getMaxCharge() != null && rule.getMaxCharge().signum() > 0 && amount.compareTo(rule.getMaxCharge()) > 0) {
            detail.append(" → 封顶 ").append(plain(rule.getMaxCharge()));
            amount = rule.getMaxCharge();
        }
        r.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        r.setDetail(detail.toString());
        return r;
    }

    private static RateTier find(List<RateTier> tiers, BigDecimal qty) {
        for (RateTier t : tiers) {
            boolean lower = qty.compareTo(t.getFromQty()) > 0 || (t.getFromQty().signum() == 0 && qty.signum() == 0);
            boolean upper = t.getToQty() == null || qty.compareTo(t.getToQty()) <= 0;
            if (lower && upper) {
                return t;
            }
        }
        throw new BizException("数量 " + plain(qty) + " 未落入任何阶梯区间");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
