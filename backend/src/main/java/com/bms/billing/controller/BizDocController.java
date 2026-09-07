package com.bms.billing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bms.billing.dto.ReasonRequest;
import com.bms.billing.entity.BizDoc;
import com.bms.billing.entity.Fee;
import com.bms.billing.mapper.BizDocMapper;
import com.bms.billing.mapper.FeeMapper;
import com.bms.billing.service.BillingService;
import com.bms.common.BizException;
import com.bms.common.Csv;
import com.bms.common.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/doc")
@RequiredArgsConstructor
public class BizDocController {
    private final BizDocMapper mapper;
    private final FeeMapper feeMapper;
    private final BillingService service;

    private LambdaQueryWrapper<BizDoc> query(String keyword, String source, String bizType, String customerCode, String supplierCode,
                                             String warehouseCode, String billStatus, LocalDate from, LocalDate to) {
        return new LambdaQueryWrapper<BizDoc>()
                .and(StringUtils.isNotBlank(keyword), w -> w.like(BizDoc::getDocNo, keyword).or().like(BizDoc::getExtRef, keyword))
                .eq(StringUtils.isNotBlank(source), BizDoc::getSource, source)
                .eq(StringUtils.isNotBlank(bizType), BizDoc::getBizType, bizType)
                .eq(StringUtils.isNotBlank(customerCode), BizDoc::getCustomerCode, customerCode)
                .eq(StringUtils.isNotBlank(supplierCode), BizDoc::getSupplierCode, supplierCode)
                .eq(StringUtils.isNotBlank(warehouseCode), BizDoc::getWarehouseCode, warehouseCode)
                .eq(StringUtils.isNotBlank(billStatus), BizDoc::getBillStatus, billStatus)
                .ge(from != null, BizDoc::getBizDate, from)
                .le(to != null, BizDoc::getBizDate, to)
                .orderByDesc(BizDoc::getId);
    }

    @GetMapping("/page")
    public R<Page<BizDoc>> page(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "20") long size,
                                @RequestParam(required = false) String keyword, @RequestParam(required = false) String source,
                                @RequestParam(required = false) String bizType, @RequestParam(required = false) String customerCode,
                                @RequestParam(required = false) String supplierCode, @RequestParam(required = false) String warehouseCode,
                                @RequestParam(required = false) String billStatus,
                                @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return R.ok(mapper.selectPage(new Page<>(current, size), query(keyword, source, bizType, customerCode, supplierCode, warehouseCode, billStatus, from, to)));
    }

    @GetMapping("/status-count")
    public R<Map<String, Long>> statusCount() {
        Map<String, Long> m = new HashMap<>();
        for (String s : new String[]{BillingService.PENDING, BillingService.BILLED, BillingService.FAILED, BillingService.IGNORED}) {
            m.put(s, mapper.selectCount(new LambdaQueryWrapper<BizDoc>().eq(BizDoc::getBillStatus, s)));
        }
        return R.ok(m);
    }

    @Data
    public static class DocDetail {
        private BizDoc doc;
        private List<Fee> fees;
    }

    @GetMapping("/{docNo}")
    public R<DocDetail> get(@PathVariable String docNo) {
        DocDetail d = new DocDetail();
        d.setDoc(service.requireDoc(docNo));
        d.setFees(feeMapper.selectList(new LambdaQueryWrapper<Fee>().eq(Fee::getDocNo, docNo).orderByAsc(Fee::getId)));
        return R.ok(d);
    }

    @PostMapping
    public R<BizDoc> create(@RequestBody BizDoc doc, @RequestParam(defaultValue = "true") boolean autoBill) {
        doc.setSource("MANUAL");
        return R.ok(service.createDoc(doc, autoBill));
    }

    @PostMapping("/{docNo}/bill")
    public R<BizDoc> bill(@PathVariable String docNo) {
        return R.ok(service.bill(docNo));
    }

    @PostMapping("/bill-pending")
    public R<Integer> billPending() {
        return R.ok(service.billPending());
    }

    @PostMapping("/{docNo}/ignore")
    public R<BizDoc> ignore(@PathVariable String docNo, @RequestBody(required = false) ReasonRequest req) {
        return R.ok(service.ignore(docNo, req == null ? null : req.getReason()));
    }

    @PostMapping("/{docNo}/reopen")
    public R<BizDoc> reopen(@PathVariable String docNo) {
        return R.ok(service.reopen(docNo));
    }

    private static final String[] IMPORT_HEADERS = {"extRef", "bizType", "customerCode", "supplierCode", "warehouseCode", "bizDate",
            "orders", "lines", "qty", "boxes", "pallets", "weight", "volume", "distance", "days", "origin", "destination", "remark"};

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"WMS-OUT-0001", "OUTBOUND", "CUST-001", "SF", "WH-SH", LocalDate.now(), 1, 3, 25, 2, 0, 12.5, 0.3, 0, 1, "", "", "示例"});
        return Csv.download("biz_doc_template.csv", IMPORT_HEADERS, rows, r -> r);
    }

    @Data
    public static class ImportResult {
        private int total;
        private int success;
        private int skipped;
        private List<String> errors = new ArrayList<>();
    }

    @PostMapping("/import")
    public R<ImportResult> importCsv(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "true") boolean autoBill) throws IOException {
        List<String[]> rows = Csv.read(file.getInputStream());
        if (rows.size() < 2) {
            throw new BizException("CSV 无数据行");
        }
        Map<String, Integer> idx = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim(), i);
        }
        for (String h : new String[]{"bizType", "customerCode", "bizDate"}) {
            if (!idx.containsKey(h)) {
                throw new BizException("CSV 缺少列: " + h);
            }
        }
        ImportResult result = new ImportResult();
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            result.total++;
            try {
                BizDoc d = new BizDoc();
                d.setSource("IMPORT");
                d.setExtRef(cell(r, idx, "extRef"));
                d.setBizType(cell(r, idx, "bizType"));
                d.setCustomerCode(cell(r, idx, "customerCode"));
                d.setSupplierCode(cell(r, idx, "supplierCode"));
                d.setWarehouseCode(cell(r, idx, "warehouseCode"));
                d.setBizDate(LocalDate.parse(cell(r, idx, "bizDate")));
                d.setOrders(intOf(cell(r, idx, "orders"), 1));
                d.setLines(intOf(cell(r, idx, "lines"), 0));
                d.setQty(dec(cell(r, idx, "qty")));
                d.setBoxes(dec(cell(r, idx, "boxes")));
                d.setPallets(dec(cell(r, idx, "pallets")));
                d.setWeight(dec(cell(r, idx, "weight")));
                d.setVolume(dec(cell(r, idx, "volume")));
                d.setDistance(dec(cell(r, idx, "distance")));
                d.setDays(intOf(cell(r, idx, "days"), 1));
                d.setOrigin(cell(r, idx, "origin"));
                d.setDestination(cell(r, idx, "destination"));
                d.setRemark(cell(r, idx, "remark"));
                long before = mapper.selectCount(null);
                service.createDoc(d, autoBill);
                if (mapper.selectCount(null) == before) {
                    result.skipped++;
                } else {
                    result.success++;
                }
            } catch (RuntimeException e) {
                result.getErrors().add("第 " + (i + 1) + " 行: " + e.getMessage());
            }
        }
        return R.ok(result);
    }

    private static String cell(String[] r, Map<String, Integer> idx, String name) {
        Integer i = idx.get(name);
        if (i == null || i >= r.length) {
            return null;
        }
        String v = r[i].trim();
        return v.isEmpty() ? null : v;
    }

    private static int intOf(String v, int def) {
        return v == null ? def : new BigDecimal(v).intValue();
    }

    private static BigDecimal dec(String v) {
        return v == null ? BigDecimal.ZERO : new BigDecimal(v);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword, @RequestParam(required = false) String source,
                                         @RequestParam(required = false) String bizType, @RequestParam(required = false) String customerCode,
                                         @RequestParam(required = false) String supplierCode, @RequestParam(required = false) String warehouseCode,
                                         @RequestParam(required = false) String billStatus,
                                         @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        List<BizDoc> list = mapper.selectList(query(keyword, source, bizType, customerCode, supplierCode, warehouseCode, billStatus, from, to));
        String[] headers = {"单据号", "来源", "外部单号", "业务类型", "客户", "承运商/供应商", "仓库", "业务日期", "单数", "行数", "件数", "箱数", "托数", "重量", "体积", "里程", "天数", "计费状态", "应收", "应付", "失败原因"};
        return Csv.download("biz_docs.csv", headers, list, d -> new Object[]{d.getDocNo(), d.getSource(), d.getExtRef(), d.getBizType(),
                d.getCustomerCode(), d.getSupplierCode(), d.getWarehouseCode(), d.getBizDate(), d.getOrders(), d.getLines(), d.getQty(), d.getBoxes(),
                d.getPallets(), d.getWeight(), d.getVolume(), d.getDistance(), d.getDays(), d.getBillStatus(), d.getArAmount(), d.getApAmount(), d.getFailReason()});
    }
}
