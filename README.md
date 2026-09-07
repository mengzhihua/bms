# BMS 结算管理系统

OTWB 供应链平台中的计费与结算中枢：接收 WMS / TMS / OMS 的业务单据（入库、出库、仓储、运输、增值、退货），
按客户（应收 AR）与承运商/供应商（应付 AP）合同中的费率规则自动计费，生成费用明细、对账单、发票与收付款核销，
并提供收入/成本/毛利、账龄等经营报表。与本组织的 WMS / TMS / OMS 系统同构（Spring Boot 2.7 + MyBatis-Plus / Vue 3 + Element Plus）。

## 功能范围

| 模块 | 能力 |
| --- | --- |
| 基础数据 | 结算对象（客户/承运商/供应商，结算周期、信用额度）、仓库、费用项目（方向 AR/AP/BOTH、默认税率） |
| 合同与费率 | AR/AP 合同（有效期、结算周期、税率、账期），状态机 DRAFT→ACTIVE→EXPIRED/TERMINATED；费率规则按业务类型 × 费用项目 × 仓库范围，计费单位（单/行/件/箱/托/重量/体积/里程/托·天/方·天/件·天），计价模式 固定 / 单价 / 阶梯(整段) / 累进(分段)，最低/最高收费、优先级 |
| 计费中心 | 业务单据接入（Open API 推送、CSV 导入、手工录入，按 source+extRef 幂等），自动匹配有效合同生成 AR/AP 费用，计算过程留痕；重算、忽略/恢复、批量计费；手工费用与调整费用、费用作废 |
| 对账结算 | 按结算对象 × 期间生成对账单（可批量），草稿期可移除费用/追加调整，确认 → 争议 → 结清；发票登记与作废（校验对账单余额）；收/付款登记、核销/反核销、结清判定 |
| 系统集成 | `/api/open/wms|tms|oms/docs` 业务单据推送（`X-Api-Key`），推送查询，集成日志 |
| 报表 | 工作台（待计费/失败单据、本月 AR/AP、未对账、应收/应付余额、逾期、即将到期合同、趋势、Top 客户）、费用汇总、费用项目构成、收入成本毛利、账龄分析、结算对象台账、对账单 CSV 导出 |
| 系统管理 | 用户与角色（ADMIN / OPERATOR / VIEWER）、操作日志 |

## 目录

```text
backend/   Spring Boot 2.7 + MyBatis-Plus 后端，端口 8080
frontend/  Vue 3 + Vite + Element Plus 前端，端口 5173（/api 代理到后端）
scripts/   smoke.sh 端到端冒烟脚本
```

## 快速开始

### 后端

要求 JDK 17 和 Maven：

```bash
cd backend
mvn spring-boot:run
```

默认 H2 文件库 `backend/data/bms`，启动时自动执行 `schema.sql`（建表）与 `data.sql`（幂等演示数据：
客户/承运商/供应商、仓库、费用项目、AR/AP 合同及费率规则与阶梯）。默认管理员 `admin / admin123`。
MySQL 通过 `--spring.profiles.active=mysql` 启用（`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`）。

主要环境变量：

| 变量 | 说明 |
| --- | --- |
| `BMS_AUTH_SECRET` / `BMS_TOKEN_TTL` | 登录令牌签名密钥与有效期 |
| `BMS_ADMIN_PASSWORD` | 首次启动初始化的管理员密码 |
| `BMS_OPEN_API_KEY` | `/api/open/**` 开放接口的 `X-Api-Key`（默认 `bms-open-key`，生产必须修改） |
| `BMS_CORS_ORIGINS` / `BMS_H2_CONSOLE` | 跨域来源、是否开启 H2 控制台 |

### 前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build
```

### 测试与冒烟

```bash
cd backend && mvn test                 # 计费引擎/流程测试
scripts/smoke.sh [http://localhost:8080]   # 需要后端已启动，依赖 curl / jq / bc
```

冒烟流程：登录 → 基础数据/合同 → WMS 出库单、TMS 运单推送并自动计费（幂等、无合同失败）→ 手工费用 →
生成对账单、移除费用、追加调整、确认 → 开票 → 收款核销 → 对账单结清 → 报表。

## 计费说明

- 单据字段 `orders/lines/qty/boxes/pallets/weight/volume/distance/storageDays` 对应不同计费单位；
  `PALLET_DAY/VOLUME_DAY/PIECE_DAY` 为数量 × 天数。
- 同一合同下多条规则匹配时按 `priority` 取最优；`warehouseCode` 为空的规则适用所有仓库。
- 阶梯 (TIERED)：按总量落入区间的单价 × 总量；累进 (PROGRESSIVE)：每段分别计价后求和；
  首段 `fromQty=0` 且 `toQty>0` 的累进段作为起步价整段收取。
- 金额四舍五入保留 2 位，税额 = 不含税 × 合同税率（费用项目默认税率作为手工费用默认值）。
