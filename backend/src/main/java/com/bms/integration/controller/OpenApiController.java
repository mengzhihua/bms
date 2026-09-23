package com.bms.integration.controller;

import com.bms.billing.controller.BizDocController;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.BizDocMapper;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.common.R;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.bms.integration.service.IntegrationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放接口（X-Api-Key）：上游 WMS / TMS / OMS 推送业务单据，BMS 自动计费。
 * <ul>
 *   <li>POST /api/open/wms/docs   入库/出库/退货/仓储快照/增值 单据（批量）</li>
 *   <li>POST /api/open/tms/docs   运单（运输）单据（批量）</li>
 *   <li>POST /api/open/oms/docs   OMS 订单类单据（批量，等同 wms 但来源标记 OMS）</li>
 *   <li>GET  /api/open/docs/{source}/{extRef}  查询单据计费结果</li>
 *   <li>GET  /api/open/cost/records            IR 控制塔拉取费用快照</li>
 *   <li>GET  /api/open/ir/snapshots            IR 控制塔标准快照（costs + COST 行）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenApiController {
    private final BillingService billingService;
    private final FeeMapper feeMapper;
    private final BizDocMapper docMapper;
    private final IntegrationLogService logService;

    @Data
    public static class DocPush {
        @NotBlank
        private String extRef;
        /** WMS/OMS 必填；TMS 可省略，默认 TRANSPORT */
        private String bizType;
        @NotBlank
        private String customerCode;
        private String supplierCode;
        private String warehouseCode;
        private LocalDate bizDate;
        private Integer orders;
        private Integer lines;
        private BigDecimal qty;
        private BigDecimal boxes;
        private BigDecimal pallets;
        private BigDecimal weight;
        private BigDecimal volume;
        private BigDecimal distance;
        private Integer days;
        private String origin;
        private String destination;
        private String remark;
        /** 调用方已经算好的金额。有值时不再按合同重算。 */
        private BigDecimal statedAmount;
        /** AR 或 AP，缺省 AP */
        private String direction;
        private String chargeItemCode;
    }

    @Data
    public static class PushResult {
        private String extRef;
        private String docNo;
        private String billStatus;
        private BigDecimal arAmount;
        private BigDecimal apAmount;
        private String error;
    }

    @PostMapping("/wms/docs")
    public R<List<PushResult>> wms(@RequestBody List<@Valid DocPush> docs) {
        return R.ok(push("WMS", docs));
    }

    @PostMapping("/tms/docs")
    public R<List<PushResult>> tms(@RequestBody List<@Valid DocPush> docs) {
        if (docs != null) {
            for (DocPush d : docs) {
                if (d.getBizType() == null || d.getBizType().trim().isEmpty()) {
                    d.setBizType("TRANSPORT");
                }
            }
        }
        return R.ok(push("TMS", docs));
    }

    @PostMapping("/oms/docs")
    public R<List<PushResult>> oms(@RequestBody List<@Valid DocPush> docs) {
        return R.ok(push("OMS", docs));
    }

    /** 子系统直连用 WMS 仓号，结算单仍记控制塔仓号。 */
    static String warehouse(String code) {
        if (code == null) {
            return null;
        }
        String value = code.trim();
        if ("WH01".equals(value)) {
            return "WH-SH";
        }
        if ("WH02".equals(value)) {
            return "WH-BJ";
        }
        if ("WH03".equals(value)) {
            return "WH-GZ";
        }
        return value;
    }

    private List<PushResult> push(String source, List<DocPush> docs) {
        if (docs == null || docs.isEmpty()) {
            throw new BizException("单据列表为空");
        }
        List<PushResult> out = new ArrayList<>();
        for (DocPush p : docs) {
            PushResult r = new PushResult();
            r.setExtRef(p.getExtRef());
            String error = null;
            try {
                BizDoc d = new BizDoc();
                d.setSource(source);
                d.setExtRef(p.getExtRef());
                d.setBizType(p.getBizType());
                d.setCustomerCode(p.getCustomerCode());
                d.setSupplierCode(p.getSupplierCode());
                d.setWarehouseCode(warehouse(p.getWarehouseCode()));
                d.setBizDate(p.getBizDate());
                d.setOrders(p.getOrders());
                d.setLines(p.getLines());
                d.setQty(p.getQty());
                d.setBoxes(p.getBoxes());
                d.setPallets(p.getPallets());
                d.setWeight(p.getWeight());
                d.setVolume(p.getVolume());
                d.setDistance(p.getDistance());
                d.setDays(p.getDays());
                d.setOrigin(p.getOrigin());
                d.setDestination(p.getDestination());
                d.setRemark(p.getRemark());
                boolean stated = p.getStatedAmount() != null;
                BizDoc saved = billingService.createDoc(d, !stated);
                if (stated && !BillingService.BILLED.equals(saved.getBillStatus())) {
                    saved = billingService.bookStated(saved, p.getDirection(), p.getChargeItemCode(),
                            p.getStatedAmount(), p.getRemark());
                }
                r.setDocNo(saved.getDocNo());
                r.setBillStatus(saved.getBillStatus());
                r.setArAmount(saved.getArAmount());
                r.setApAmount(saved.getApAmount());
                r.setError(saved.getFailReason());
            } catch (RuntimeException e) {
                error = e.getMessage();
                r.setError(error);
            }
            logService.inbound(source, "PUSH_DOC", p.getExtRef(), p, r, error);
            out.add(r);
        }
        return out;
    }

    @GetMapping("/docs/{source}/{extRef}")
    public R<BizDocController.DocDetail> get(@PathVariable String source, @PathVariable String extRef) {
        BizDoc d = billingService.findByRef(source, extRef);
        if (d == null) {
            throw new BizException("单据不存在");
        }
        BizDocController.DocDetail detail = new BizDocController.DocDetail();
        detail.setDoc(d);
        detail.setFees(feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, d.getDocNo())));
        return R.ok(detail);
    }

    /** IR 控制塔成本快照：未作废费用按业务日输出。 */
    @GetMapping("/cost/records")
    public R<List<Map<String, Object>>> costRecords(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return R.ok(costRecordRows(from, to));
    }

    /** IR 控制塔标准口：费用明细同时以 costs 数组和 COST 快照行给出。 */
    @GetMapping("/ir/snapshots")
    public R<Map<String, Object>> irSnapshots(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(90) : from;
        List<Map<String, Object>> costs = costRecordRows(start, end);
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Map<String, Object> cost : costs) {
            snapshots.add(costSnapshot(cost));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", "BMS");
        data.put("costs", costs);
        data.put("snapshots", snapshots);
        return R.ok(data);
    }

    private List<Map<String, Object>> costRecordRows(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BizException("from/to 日期不合法");
        }
        List<Fee> fees = feeMapper.selectList(new LambdaQueryWrapper<Fee>()
                .ne(Fee::getStatus, "CANCELLED")
                .ge(Fee::getBizDate, from)
                .le(Fee::getBizDate, to)
                .orderByAsc(Fee::getBizDate));
        Map<String, BizDoc> docs = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Fee fee : fees) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("feeNo", fee.getFeeNo());
            row.put("bizDate", fee.getBizDate());
            row.put("orderNo", fee.getDocNo());
            row.put("warehouseCode", fee.getWarehouseCode());
            row.put("carrierCode", carrierOf(fee, null));
            row.put("costType", fee.getBizType() != null ? fee.getBizType() : fee.getChargeItemCode());
            row.put("amount", fee.getTotalAmount() != null ? fee.getTotalAmount() : fee.getAmount());
            row.put("status", fee.getStatus());
            row.put("sourceSystem", "BMS");
            row.put("remark", fee.getRemark());
            if (fee.getDocNo() != null) {
                BizDoc doc = docs.computeIfAbsent(fee.getDocNo(), this::loadDoc);
                if (doc != null) {
                    row.put("orderNo", doc.getExtRef() != null ? doc.getExtRef() : doc.getDocNo());
                    if (row.get("warehouseCode") == null) {
                        row.put("warehouseCode", doc.getWarehouseCode());
                    }
                    if (row.get("costType") == null) {
                        row.put("costType", doc.getBizType());
                    }
                    if (row.get("carrierCode") == null) {
                        row.put("carrierCode", carrierOf(fee, doc));
                    }
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private static Map<String, Object> costSnapshot(Map<String, Object> cost) {
        Map<String, Object> row = new LinkedHashMap<>(cost);
        Object feeNo = cost.get("feeNo");
        Object orderNo = cost.get("orderNo");
        Object costType = cost.get("costType");
        row.put("dataType", "COST");
        row.put("bizKey", feeNo != null ? feeNo : orderNo + "/" + costType);
        row.put("status", cost.get("status") == null ? "NEW" : cost.get("status"));
        row.put("sku", costType);
        row.put("qty", BigDecimal.ONE);
        row.put("plantCode", cost.get("warehouseCode"));
        row.put("title", cost.get("remark"));
        return row;
    }

    private BizDoc loadDoc(String docNo) {
        return docMapper.selectOne(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getDocNo, docNo));
    }

    private String carrierOf(Fee fee, BizDoc doc) {
        if (fee != null && transport(fee.getBizType(), fee.getChargeItemCode())
                && notBlank(fee.getPartnerCode())) {
            return fee.getPartnerCode();
        }
        if (doc != null && transport(doc.getBizType(), fee == null ? null : fee.getChargeItemCode())
                && notBlank(doc.getSupplierCode())) {
            return doc.getSupplierCode();
        }
        return null;
    }

    private static boolean transport(String bizType, String chargeItemCode) {
        return "TRANSPORT".equals(bizType) || "FREIGHT".equals(chargeItemCode);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
