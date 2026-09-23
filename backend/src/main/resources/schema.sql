-- BMS 结算管理系统 表结构（H2 MySQL 模式 / MySQL 兼容）

-- ===================== 基础数据 =====================
CREATE TABLE IF NOT EXISTS bms_partner (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  type VARCHAR(16) NOT NULL,            -- CUSTOMER 客户(应收) / CARRIER 承运商(应付) / SUPPLIER 供应商(应付)
  contact VARCHAR(64),
  phone VARCHAR(32),
  tax_no VARCHAR(64),
  bank_account VARCHAR(128),
  address VARCHAR(255),
  settle_cycle VARCHAR(16) DEFAULT 'MONTHLY',   -- WEEKLY / HALF_MONTH / MONTHLY
  credit_limit DECIMAL(14,2) DEFAULT 0,
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_partner_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS bms_warehouse (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  city VARCHAR(64),
  address VARCHAR(255),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_warehouse_code UNIQUE (code)
);

-- 费用项目（费用科目）
CREATE TABLE IF NOT EXISTS bms_charge_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  category VARCHAR(16) NOT NULL,        -- STORAGE 仓储 / HANDLING 操作 / TRANSPORT 运输 / VAS 增值 / OTHER 其他
  direction VARCHAR(8) DEFAULT 'BOTH',  -- AR 应收 / AP 应付 / BOTH
  default_tax_rate DECIMAL(6,4) DEFAULT 0.06,
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_charge_item_code UNIQUE (code)
);

-- ===================== 合同与费率 =====================
CREATE TABLE IF NOT EXISTS bms_contract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_no VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  direction VARCHAR(8) NOT NULL,        -- AR 应收合同(客户) / AP 应付合同(承运商/供应商)
  partner_code VARCHAR(32) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  settle_cycle VARCHAR(16) DEFAULT 'MONTHLY',
  currency VARCHAR(8) DEFAULT 'CNY',
  tax_rate DECIMAL(6,4) DEFAULT 0.06,
  payment_days INT DEFAULT 30,          -- 账期（天）
  status VARCHAR(16) DEFAULT 'DRAFT',   -- DRAFT / ACTIVE / EXPIRED / TERMINATED
  remark VARCHAR(500),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_contract_no UNIQUE (contract_no),
  KEY idx_contract_partner (partner_code, direction, status)
);

-- 费率规则：某合同下、某业务类型、某费用项目如何计费
CREATE TABLE IF NOT EXISTS bms_rate_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  charge_item_code VARCHAR(32) NOT NULL,
  biz_type VARCHAR(16) NOT NULL,        -- INBOUND 入库 / OUTBOUND 出库 / STORAGE 仓储(日快照) / TRANSPORT 运输 / VAS 增值 / RETURN 退货
  warehouse_code VARCHAR(32),           -- 为空表示所有仓库
  unit VARCHAR(16) NOT NULL,            -- ORDER 单 / LINE 行 / PIECE 件 / BOX 箱 / PALLET 托 / WEIGHT 公斤 / VOLUME 方 / DISTANCE 公里 / PALLET_DAY 托·天 / VOLUME_DAY 方·天 / PIECE_DAY 件·天
  price_mode VARCHAR(16) NOT NULL,      -- FIXED 每单固定 / UNIT 单价×数量 / TIERED 阶梯(全量) / PROGRESSIVE 累进
  unit_price DECIMAL(14,4) DEFAULT 0,
  min_charge DECIMAL(14,2) DEFAULT 0,   -- 最低收费
  max_charge DECIMAL(14,2),             -- 封顶（可空）
  priority INT DEFAULT 0,
  status INT DEFAULT 1,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  KEY idx_rate_rule_contract (contract_id, biz_type)
);

CREATE TABLE IF NOT EXISTS bms_rate_tier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT NOT NULL,
  from_qty DECIMAL(14,4) NOT NULL,      -- 区间下限（不含）
  to_qty DECIMAL(14,4),                 -- 区间上限（含），空=无上限
  price DECIMAL(14,4) NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  KEY idx_rate_tier_rule (rule_id)
);

-- ===================== 业务单据（计费来源） =====================
CREATE TABLE IF NOT EXISTS bms_biz_doc (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_no VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,          -- WMS / TMS / OMS / MANUAL / IMPORT
  ext_ref VARCHAR(64),                  -- 来源系统单号（幂等键：source+ext_ref）
  biz_type VARCHAR(16) NOT NULL,
  customer_code VARCHAR(32) NOT NULL,   -- 货主/客户（应收对象）
  supplier_code VARCHAR(32),            -- 承运商/供应商（应付对象，可空）
  warehouse_code VARCHAR(32),
  biz_date DATE NOT NULL,
  orders INT DEFAULT 1,
  lines INT DEFAULT 0,
  qty DECIMAL(14,4) DEFAULT 0,
  boxes DECIMAL(14,4) DEFAULT 0,
  pallets DECIMAL(14,4) DEFAULT 0,
  weight DECIMAL(14,4) DEFAULT 0,
  volume DECIMAL(14,4) DEFAULT 0,
  distance DECIMAL(14,4) DEFAULT 0,
  days INT DEFAULT 1,                   -- 仓储快照天数
  origin VARCHAR(64),
  destination VARCHAR(64),
  bill_status VARCHAR(16) DEFAULT 'PENDING',  -- PENDING 待计费 / BILLED 已计费 / FAILED 计费失败 / IGNORED 忽略
  fail_reason VARCHAR(500),
  ar_amount DECIMAL(14,2) DEFAULT 0,
  ap_amount DECIMAL(14,2) DEFAULT 0,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_biz_doc_no UNIQUE (doc_no),
  UNIQUE KEY uk_biz_doc_ref (source, ext_ref),
  KEY idx_biz_doc_customer (customer_code, biz_date),
  KEY idx_biz_doc_status (bill_status)
);

-- ===================== 费用明细 =====================
CREATE TABLE IF NOT EXISTS bms_fee (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_no VARCHAR(32) NOT NULL,
  direction VARCHAR(8) NOT NULL,        -- AR / AP
  partner_code VARCHAR(32) NOT NULL,
  contract_no VARCHAR(32),
  rule_id BIGINT,
  doc_no VARCHAR(32),
  biz_type VARCHAR(16),
  warehouse_code VARCHAR(32),
  charge_item_code VARCHAR(32) NOT NULL,
  biz_date DATE NOT NULL,
  unit VARCHAR(16),
  qty DECIMAL(14,4) DEFAULT 0,
  unit_price DECIMAL(14,4) DEFAULT 0,
  amount DECIMAL(14,2) NOT NULL,        -- 不含税
  tax_rate DECIMAL(6,4) DEFAULT 0,
  tax_amount DECIMAL(14,2) DEFAULT 0,
  total_amount DECIMAL(14,2) NOT NULL,  -- 含税
  source VARCHAR(16) DEFAULT 'AUTO',    -- AUTO 自动计费 / MANUAL 手工 / ADJUST 调整 / STATED 调用方声明金额
  status VARCHAR(16) DEFAULT 'NEW',     -- NEW 未对账 / STATEMENTED 已对账 / SETTLED 已结算 / CANCELLED 作废
  statement_no VARCHAR(32),
  calc_detail VARCHAR(500),             -- 计费过程说明
  remark VARCHAR(255),
  created_by VARCHAR(64),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_fee_no UNIQUE (fee_no),
  KEY idx_fee_partner (direction, partner_code, status, biz_date),
  KEY idx_fee_doc (doc_no),
  KEY idx_fee_statement (statement_no)
);

-- ===================== 对账 / 发票 / 收付款 =====================
CREATE TABLE IF NOT EXISTS bms_statement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  statement_no VARCHAR(32) NOT NULL,
  direction VARCHAR(8) NOT NULL,
  partner_code VARCHAR(32) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  fee_count INT DEFAULT 0,
  amount DECIMAL(14,2) DEFAULT 0,
  tax_amount DECIMAL(14,2) DEFAULT 0,
  total_amount DECIMAL(14,2) DEFAULT 0,
  adjust_amount DECIMAL(14,2) DEFAULT 0,
  paid_amount DECIMAL(14,2) DEFAULT 0,
  invoiced_amount DECIMAL(14,2) DEFAULT 0,
  due_date DATE,
  status VARCHAR(16) DEFAULT 'DRAFT',   -- DRAFT 草稿 / CONFIRMED 已确认 / DISPUTED 争议 / SETTLED 已结清 / CANCELLED 作废
  confirmed_at TIMESTAMP,
  confirmed_by VARCHAR(64),
  dispute_reason VARCHAR(500),
  remark VARCHAR(500),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_statement_no UNIQUE (statement_no),
  KEY idx_statement_partner (direction, partner_code, status)
);

CREATE TABLE IF NOT EXISTS bms_statement_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  statement_no VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_status VARCHAR(16),
  to_status VARCHAR(16),
  operator VARCHAR(64),
  remark VARCHAR(500),
  created_at TIMESTAMP,
  KEY idx_statement_log_no (statement_no)
);

CREATE TABLE IF NOT EXISTS bms_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_no VARCHAR(32) NOT NULL,      -- 系统流水号
  invoice_code VARCHAR(64),             -- 税务发票号码
  direction VARCHAR(8) NOT NULL,        -- AR 销项 / AP 进项
  partner_code VARCHAR(32) NOT NULL,
  statement_no VARCHAR(32) NOT NULL,
  invoice_type VARCHAR(16) DEFAULT 'SPECIAL',  -- SPECIAL 专票 / NORMAL 普票 / ELECTRONIC 电子
  amount DECIMAL(14,2) DEFAULT 0,
  tax_amount DECIMAL(14,2) DEFAULT 0,
  total_amount DECIMAL(14,2) NOT NULL,
  invoice_date DATE NOT NULL,
  status VARCHAR(16) DEFAULT 'ISSUED',  -- ISSUED 已开 / CANCELLED 作废
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_invoice_no UNIQUE (invoice_no),
  KEY idx_invoice_statement (statement_no)
);

CREATE TABLE IF NOT EXISTS bms_payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(32) NOT NULL,
  direction VARCHAR(8) NOT NULL,        -- AR 收款 / AP 付款
  partner_code VARCHAR(32) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  applied_amount DECIMAL(14,2) DEFAULT 0,
  pay_date DATE NOT NULL,
  method VARCHAR(16) DEFAULT 'TRANSFER',  -- TRANSFER 转账 / CHECK 支票 / CASH 现金 / OFFSET 抵扣
  bank_ref VARCHAR(64),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_payment_no UNIQUE (payment_no)
);

-- 核销：收付款 → 对账单
CREATE TABLE IF NOT EXISTS bms_payment_apply (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(32) NOT NULL,
  statement_no VARCHAR(32) NOT NULL,
  amount DECIMAL(14,2) NOT NULL,
  operator VARCHAR(64),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  KEY idx_payment_apply_stmt (statement_no)
);

-- ===================== 集成 =====================
CREATE TABLE IF NOT EXISTS bms_integration_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  direction VARCHAR(8) NOT NULL,
  target VARCHAR(16) NOT NULL,
  action VARCHAR(32) NOT NULL,
  ref_no VARCHAR(64),
  request_body TEXT,
  response_body TEXT,
  success INT DEFAULT 1,
  error_msg VARCHAR(500),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  KEY idx_integration_ref (ref_no)
);

-- ===================== 系统 =====================
CREATE TABLE IF NOT EXISTS bms_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  real_name VARCHAR(64),
  role VARCHAR(16) NOT NULL,
  status INT DEFAULT 1,
  last_login_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_user_name UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS bms_sequence (
  prefix VARCHAR(16) NOT NULL,
  day_key VARCHAR(8) NOT NULL,
  seq_value INT NOT NULL,
  PRIMARY KEY (prefix, day_key)
);

CREATE TABLE IF NOT EXISTS bms_op_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64),
  method VARCHAR(8),
  path VARCHAR(255),
  query VARCHAR(255),
  http_status INT,
  cost_ms INT,
  client_ip VARCHAR(64),
  created_at TIMESTAMP,
  KEY idx_op_log_created (created_at)
);
