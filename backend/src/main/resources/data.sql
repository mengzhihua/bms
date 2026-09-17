-- 演示数据（幂等：已存在则跳过）
-- ===================== 结算对象 =====================
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'CUST-001', '华东电商科技有限公司', 'CUSTOMER', '王经理', '13800000001', '91310000MA1K00001X', 'MONTHLY', 500000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'CUST-001');
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'CUST-002', '北方快消品牌有限公司', 'CUSTOMER', '李总', '13800000002', '91110000MA1K00002X', 'HALF_MONTH', 300000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'CUST-002');
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'CUST-003', '南方家电连锁', 'CUSTOMER', '张主管', '13800000003', '91440000MA1K00003X', 'MONTHLY', 200000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'CUST-003');
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'SF', '顺丰速运', 'CARRIER', '客户经理', '95338', '91440300MA5K00004X', 'MONTHLY', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'SF');
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'DEPPON', '德邦物流', 'CARRIER', '客户经理', '95353', '91310000MA5K00005X', 'MONTHLY', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'DEPPON');
INSERT INTO bms_partner (code, name, type, contact, phone, tax_no, settle_cycle, credit_limit, status, created_at, updated_at)
SELECT 'SUP-LABOR', '嘉禾劳务外包', 'SUPPLIER', '周队长', '13900000006', '91320000MA5K00006X', 'MONTHLY', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_partner WHERE code = 'SUP-LABOR');

-- ===================== 仓库 =====================
INSERT INTO bms_warehouse (code, name, city, status, created_at, updated_at)
SELECT 'WH-SH', '上海中心仓', '上海', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_warehouse WHERE code = 'WH-SH');
INSERT INTO bms_warehouse (code, name, city, status, created_at, updated_at)
SELECT 'WH-BJ', '北京分仓', '北京', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_warehouse WHERE code = 'WH-BJ');
INSERT INTO bms_warehouse (code, name, city, status, created_at, updated_at)
SELECT 'WH-GZ', '广州分仓', '广州', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_warehouse WHERE code = 'WH-GZ');

-- ===================== 费用项目 =====================
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'STORAGE', '仓储费', 'STORAGE', 'BOTH', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'STORAGE');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'INBOUND_HANDLING', '入库操作费', 'HANDLING', 'BOTH', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'INBOUND_HANDLING');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'OUTBOUND_HANDLING', '出库操作费', 'HANDLING', 'BOTH', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'OUTBOUND_HANDLING');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'ORDER_FEE', '订单处理费', 'HANDLING', 'AR', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'ORDER_FEE');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'PACKING', '包材费', 'VAS', 'AR', 0.13, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'PACKING');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'FREIGHT', '运费', 'TRANSPORT', 'BOTH', 0.09, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'FREIGHT');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'DELIVERY', '派送费', 'TRANSPORT', 'BOTH', 0.09, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'DELIVERY');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'RETURN_HANDLING', '退货处理费', 'HANDLING', 'AR', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'RETURN_HANDLING');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'LABEL', '贴标费', 'VAS', 'BOTH', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'LABEL');
INSERT INTO bms_charge_item (code, name, category, direction, default_tax_rate, status, created_at, updated_at)
SELECT 'OTHER', '其他费用', 'OTHER', 'BOTH', 0.06, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_charge_item WHERE code = 'OTHER');

-- ===================== 合同 =====================
-- 应收：CUST-001 仓配一体合同
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AR-2024-001', '华东电商仓配服务合同', 'AR', 'CUST-001', DATE '2024-01-01', DATE '2030-12-31', 'MONTHLY', 'CNY', 0.06, 30, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AR-2024-001');
-- 应收：CUST-002 仓储合同
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AR-2024-002', '北方快消仓储服务合同', 'AR', 'CUST-002', DATE '2024-01-01', DATE '2030-12-31', 'HALF_MONTH', 'CNY', 0.06, 15, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AR-2024-002');
-- 应收：CUST-003 草稿
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AR-2024-003', '南方家电配送合同(草稿)', 'AR', 'CUST-003', DATE '2025-01-01', DATE '2030-12-31', 'MONTHLY', 'CNY', 0.06, 30, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AR-2024-003');
-- 应付：顺丰运输合同
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AP-2024-001', '顺丰干线运输采购合同', 'AP', 'SF', DATE '2024-01-01', DATE '2030-12-31', 'MONTHLY', 'CNY', 0.09, 45, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AP-2024-001');
-- 应付：德邦运输合同
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AP-2024-002', '德邦零担运输采购合同', 'AP', 'DEPPON', DATE '2024-01-01', DATE '2030-12-31', 'MONTHLY', 'CNY', 0.09, 45, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AP-2024-002');
-- 应付：劳务外包
INSERT INTO bms_contract (contract_no, name, direction, partner_code, start_date, end_date, settle_cycle, currency, tax_rate, payment_days, status, created_at, updated_at)
SELECT 'AP-2024-003', '仓内劳务外包合同', 'AP', 'SUP-LABOR', DATE '2024-01-01', DATE '2030-12-31', 'MONTHLY', 'CNY', 0.06, 30, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_contract WHERE contract_no = 'AP-2024-003');

-- ===================== 费率规则 =====================
-- AR-2024-001: 入库 0.5元/件, 出库订单费 2元/单 + 0.3元/件(阶梯), 仓储 0.8元/托·天(最低 50), 退货 3元/单, 运费按重量累进
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'INBOUND_HANDLING', 'INBOUND', 'PIECE', 'UNIT', 0.5, 0, 0, 1, '入库按件 0.5 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'INBOUND_HANDLING');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'ORDER_FEE', 'OUTBOUND', 'ORDER', 'FIXED', 2, 0, 0, 1, '每出库单 2 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'ORDER_FEE');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'OUTBOUND_HANDLING', 'OUTBOUND', 'PIECE', 'TIERED', 0, 0, 0, 1, '出库按件阶梯: 0-10件 0.5, 10-100件 0.3, 100以上 0.2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'OUTBOUND_HANDLING');
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 0, 10, 0.5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'OUTBOUND_HANDLING' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id);
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 10, 100, 0.3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'OUTBOUND_HANDLING' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id AND t.from_qty = 10);
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 100, NULL, 0.2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'OUTBOUND_HANDLING' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id AND t.from_qty = 100);
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'STORAGE', 'STORAGE', 'PALLET_DAY', 'UNIT', 0.8, 50, 0, 1, '仓储 0.8 元/托·天，最低 50 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'STORAGE');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'RETURN_HANDLING', 'RETURN', 'ORDER', 'FIXED', 3, 0, 0, 1, '退货 3 元/单', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'RETURN_HANDLING');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'FREIGHT', 'TRANSPORT', 'WEIGHT', 'PROGRESSIVE', 0, 8, 0, 1, '运费按重量累进: 首 1kg 8 元, 1-10kg 每kg 2 元, 10kg 以上每kg 1.5 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'FREIGHT');
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 0, 1, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'FREIGHT' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id);
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 1, 10, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'FREIGHT' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id AND t.from_qty = 1);
INSERT INTO bms_rate_tier (rule_id, from_qty, to_qty, price, created_at, updated_at)
SELECT r.id, 10, NULL, 1.5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_rate_rule r JOIN bms_contract c ON c.id = r.contract_id
WHERE c.contract_no = 'AR-2024-001' AND r.charge_item_code = 'FREIGHT' AND NOT EXISTS (SELECT 1 FROM bms_rate_tier t WHERE t.rule_id = r.id AND t.from_qty = 10);

-- AR-2024-002: 仅仓储 + 入出库按托
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'STORAGE', 'STORAGE', 'VOLUME_DAY', 'UNIT', 1.2, 0, 0, 1, '仓储 1.2 元/方·天', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-002' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'STORAGE');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'INBOUND_HANDLING', 'INBOUND', 'PALLET', 'UNIT', 15, 0, 0, 1, '入库 15 元/托', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-002' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'INBOUND_HANDLING');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'OUTBOUND_HANDLING', 'OUTBOUND', 'PALLET', 'UNIT', 18, 0, 0, 1, '出库 18 元/托', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AR-2024-002' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'OUTBOUND_HANDLING');

-- AP-2024-001 顺丰：运费按重量 1.2 元/kg 最低 6 元；派送 1 元/单
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'FREIGHT', 'TRANSPORT', 'WEIGHT', 'UNIT', 1.2, 6, 0, 1, '运费 1.2 元/kg，最低 6 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AP-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'FREIGHT');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'DELIVERY', 'TRANSPORT', 'ORDER', 'FIXED', 1, 0, 0, 1, '派送 1 元/单', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AP-2024-001' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'DELIVERY');

-- AP-2024-002 德邦：运费按体积 45 元/方，最低 30 元
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'FREIGHT', 'TRANSPORT', 'VOLUME', 'UNIT', 45, 30, 0, 1, '运费 45 元/方，最低 30 元', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AP-2024-002' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'FREIGHT');

-- AP-2024-003 劳务：入库 0.2 元/件、出库 0.15 元/件（计件工资）
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'INBOUND_HANDLING', 'INBOUND', 'PIECE', 'UNIT', 0.2, 0, 0, 1, '入库计件 0.2 元/件', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AP-2024-003' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'INBOUND_HANDLING');
INSERT INTO bms_rate_rule (contract_id, charge_item_code, biz_type, unit, price_mode, unit_price, min_charge, priority, status, remark, created_at, updated_at)
SELECT c.id, 'OUTBOUND_HANDLING', 'OUTBOUND', 'PIECE', 'UNIT', 0.15, 0, 0, 1, '出库计件 0.15 元/件', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM bms_contract c
WHERE c.contract_no = 'AP-2024-003' AND NOT EXISTS (SELECT 1 FROM bms_rate_rule r WHERE r.contract_id = c.id AND r.charge_item_code = 'OUTBOUND_HANDLING');

-- IR 控制塔联调：卡单 IR-SO-STUCK 对应仓配费用，供 GET /api/open/cost/records 拉取
INSERT INTO bms_biz_doc (doc_no, source, ext_ref, biz_type, customer_code, supplier_code, warehouse_code, biz_date,
    orders, lines, qty, bill_status, ar_amount, ap_amount, remark, created_at, updated_at)
SELECT 'DOC-IR-STUCK', 'OMS', 'IR-SO-STUCK', 'OUTBOUND', 'CUST-001', 'SF', 'WH-SH', CURRENT_DATE,
    1, 1, 2, 'BILLED', 88.00, 18.00, 'IR 仓配卡单费用', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_biz_doc WHERE ext_ref = 'IR-SO-STUCK');
INSERT INTO bms_fee (fee_no, direction, partner_code, contract_no, doc_no, biz_type, warehouse_code, charge_item_code,
    biz_date, unit, qty, unit_price, amount, tax_rate, tax_amount, total_amount, source, status, remark, created_at, updated_at)
SELECT 'FEE-IR-AR-001', 'AR', 'CUST-001', 'AR-2024-001', 'DOC-IR-STUCK', 'OUTBOUND', 'WH-SH', 'OUTBOUND_HANDLING',
    CURRENT_DATE, 'PIECE', 2, 44, 88.00, 0.06, 5.28, 93.28, 'MANUAL', 'NEW', 'IR-SO-STUCK 出库操作费', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_fee WHERE fee_no = 'FEE-IR-AR-001');
INSERT INTO bms_fee (fee_no, direction, partner_code, contract_no, doc_no, biz_type, warehouse_code, charge_item_code,
    biz_date, unit, qty, unit_price, amount, tax_rate, tax_amount, total_amount, source, status, remark, created_at, updated_at)
SELECT 'FEE-IR-AP-001', 'AP', 'SF', 'AP-2024-001', 'DOC-IR-STUCK', 'TRANSPORT', 'WH-SH', 'FREIGHT',
    CURRENT_DATE, 'ORDER', 1, 18, 18.00, 0.09, 1.62, 19.62, 'MANUAL', 'NEW', 'IR-SO-STUCK 运费', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM bms_fee WHERE fee_no = 'FEE-IR-AP-001');
