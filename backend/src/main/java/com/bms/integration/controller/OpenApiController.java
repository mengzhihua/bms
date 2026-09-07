package com.bms.integration.controller;

import com.bms.billing.controller.BizDocController;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
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
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 开放接口（X-Api-Key）：上游 WMS / TMS / OMS 推送业务单据，BMS 自动计费。
 * <ul>
 *   <li>POST /api/open/wms/docs   入库/出库/退货/仓储快照/增值 单据（批量）</li>
 *   <li>POST /api/open/tms/docs   运单（运输）单据（批量）</li>
 *   <li>POST /api/open/oms/docs   OMS 订单类单据（批量，等同 wms 但来源标记 OMS）</li>
 *   <li>GET  /api/open/docs/{source}/{extRef}  查询单据计费结果</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenApiController {
    private final BillingService billingService;
    private final FeeMapper feeMapper;
    private final IntegrationLogService logService;

    @Data
    public static class DocPush {
        @NotBlank
        private String extRef;
        @NotBlank
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
        for (DocPush d : docs) {
            if (d.getBizType() == null) {
                d.setBizType("TRANSPORT");
            }
        }
        return R.ok(push("TMS", docs));
    }

    @PostMapping("/oms/docs")
    public R<List<PushResult>> oms(@RequestBody List<@Valid DocPush> docs) {
        return R.ok(push("OMS", docs));
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
                d.setWarehouseCode(p.getWarehouseCode());
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
                BizDoc saved = billingService.createDoc(d, true);
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
}
