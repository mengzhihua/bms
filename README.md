# BMS 结算管理系统

OTWB 供应链平台中的计费与结算中枢：接收 WMS / TMS / OMS 的业务单据（入库、出库、仓储、运输、增值、退货），
按客户（应收 AR）与承运商/供应商（应付 AP）合同中的费率规则自动计费，生成费用明细、对账单、发票与收付款核销，
并提供收入/成本/毛利、账龄等经营报表。与本组织的 WMS / TMS / OMS 系统同构（Spring Boot 2.7 + MyBatis-Plus / Vue 3 + Element Plus）。

项目亮点见 [docs/项目亮点.md](docs/项目亮点.md)。

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

## 技术栈与目录

- 后端：Java 17、Spring Boot 2.7、MyBatis-Plus 3.5、H2（开发）/ MySQL 8（生产）、Bearer Token 认证
- 前端：Vue 3、Vite、Element Plus、Vue Router、Axios

```text
backend/
  src/main/java/com/bms/
    basic/        结算对象 / 仓库 / 费用项目
    contract/     合同、费率规则与阶梯
    billing/      业务单据、计费引擎 RatingEngine、费用明细
    settlement/   对账单 / 发票 / 收付款核销
    integration/  Open API（WMS/TMS/OMS 推送）、集成日志
    report/       工作台与经营报表
    system/       用户、角色权限 AccessPolicy、令牌、操作日志
    common/       统一响应 R、异常处理、CSV、编号生成
  src/main/resources/schema.sql, data.sql   幂等建表与演示数据
 frontend/src/views/   Dashboard、basic、contract、billing、settlement、integration、report、system
 scripts/smoke.sh      端到端冒烟脚本
```

端口：后端 8080，前端开发服务器 5173（`/api` 代理到后端）。

## 快速开始

### 后端

要求 JDK 17 和 Maven：

```bash
cd backend
BMS_ADMIN_PASSWORD=admin123 BMS_OPEN_API_KEY=dev-open-key mvn spring-boot:run
```

默认 H2 文件库 `backend/data/bms`，启动时自动执行 `schema.sql`（建表）与 `data.sql`（幂等演示数据：
客户/承运商/供应商、仓库、费用项目、AR/AP 合同及费率规则与阶梯）。首次启动以 `BMS_ADMIN_PASSWORD` 创建 `admin`；
未设置时会随机生成一次性初始口令并打印到启动日志。
MySQL 通过 `--spring.profiles.active=mysql` 启用（`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`），
`schema.sql` / `data.sql` 均为幂等脚本（`CREATE TABLE IF NOT EXISTS`、索引随表定义），可重复启动。

主要环境变量：

| 变量 | 说明 |
| --- | --- |
| `BMS_AUTH_SECRET` / `BMS_TOKEN_TTL` | 登录令牌签名密钥与有效期 |
| `BMS_ADMIN_PASSWORD` | 首次启动初始化的管理员密码（为空则随机生成并输出到日志） |
| `BMS_OPEN_API_KEY` | `/api/open/**` 开放接口的 `X-Api-Key`（未设置时开放接口全部拒绝） |
| `BMS_CORS_ORIGINS` / `BMS_H2_CONSOLE` | 跨域来源、是否开启 H2 控制台 |

### 前端

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build
```

### 登录与权限

| 角色 | 权限 |
| --- | --- |
| `ADMIN` | 全部操作，含用户管理、操作日志（`/api/system/**` 仅 ADMIN 可访问） |
| `OPERATOR` | 读取全部业务数据；可维护合同、单据、费用、对账、结算；不可修改基础数据 |
| `VIEWER` | 只读 |

前端登录后令牌保存在浏览器本地，`BMS_AUTH_SECRET` 未设置时每次重启后需重新登录。

### Open API（上游系统推送）

上游 WMS / TMS / OMS 以 `X-Api-Key: $BMS_OPEN_API_KEY` 调用：

```text
POST /api/open/wms/docs   POST /api/open/tms/docs   POST /api/open/oms/docs
GET  /api/open/docs/{source}/{extRef}
```

请求体为单据数组，`source + extRef` 幂等（重复推送返回已有单据）：

```json
[{
  "extRef": "OUT-20260907-001", "bizType": "OUTBOUND", "customerCode": "CUST-001",
  "supplierCode": null, "warehouseCode": "WH-SH", "bizDate": "2026-09-07",
  "orders": 1, "lines": 5, "qty": 120, "boxes": 12, "pallets": 2,
  "weight": 350.5, "volume": 2.4, "distance": null, "days": null,
  "origin": null, "destination": null, "remark": ""
}]
```

`bizType` 取值 `INBOUND / OUTBOUND / STORAGE / TRANSPORT / VAS / RETURN`（TMS 推送缺省为 `TRANSPORT`）。
响应逐条返回 `docNo / billStatus(BILLED|FAILED) / arAmount / apAmount / error`。前端「系统集成 → 开放接口说明」页有同样的说明与示例。

### 测试与冒烟

```bash
cd backend && mvn test                 # 计费引擎/流程/并发幂等/权限测试
BMS_ADMIN_PASSWORD=admin123 BMS_OPEN_API_KEY=dev-open-key \
  scripts/smoke.sh [http://localhost:8080]   # 需要后端已启动，依赖 curl / jq
```

冒烟流程：登录 → 基础数据/合同 → WMS 出库单、TMS 运单推送并自动计费（幂等、无合同失败）→ 手工费用 →
生成对账单、移除费用、追加调整、确认 → 开票 → 收款核销 → 对账单结清 → 报表。

## 计费说明

- 单据字段 `orders/lines/qty/boxes/pallets/weight/volume/distance/storageDays` 对应不同计费单位；
  `PALLET_DAY/VOLUME_DAY/PIECE_DAY` 为数量 × 天数。
- 同一合同下多条规则匹配时按 `priority` 取最优；`warehouseCode` 为空的规则适用所有仓库。
- 阶梯 (TIERED)：按总量落入区间的单价 × 总量；累进 (PROGRESSIVE)：每段分别计价后求和；
  首段 `fromQty=0` 且 `toQty>0` 的累进段作为起步价整段收取。
- 同一合同、同一费用项目只会产生一条自动费用：优先级高者胜出，同优先级时仓库专用规则优先于通用规则。
- 任一规则计算失败时单据整体标记 `FAILED` 且不保留部分费用，修正合同后可重算。
- 金额四舍五入保留 2 位，税额 = 不含税 × 合同税率（费用项目默认税率作为手工费用默认值）。

## 单据与状态流转

```text
业务单据  PENDING → BILLED | FAILED | IGNORED          （重算 / 忽略 / 恢复）
费用明细  NEW → STATEMENTED → SETTLED ；仅 NEW 可作废为 CANCELLED
对账单    DRAFT → CONFIRMED → SETTLED ；DRAFT/CONFIRMED（未开票未收款）可退回 DISPUTED 修改后再确认；未结清可 CANCELLED
发票      ISSUED → CANCELLED（开票金额不得超过对账单剩余可开票金额）
收付款    登记 → 核销到同方向、同结算对象的已确认对账单 → 对账单全额核销后自动 SETTLED
```

对账单取消时其费用回到 `NEW` 可再次对账；已核销的收付款须先反核销才能删除。

## 控制塔对接

成本只读契约见 [技术方案](docs/技术方案.md)。

控制塔拉 `GET /api/open/cost/records?from&to`，以及 `GET /api/open/ir/snapshots`。本系统不提供 `/api/open/ir/actions`，不能被控制塔改单。

## 发布包（开箱即用）

前端生产构建打进 Spring Boot 可执行 JAR。三种用法：

### 1. 服务端（任意已装 JDK 17 的机器）

```bash
java -jar bms-backend-1.0.0.jar --server.port=8084
```

Linux systemd 示例见发布包 `README.txt`。

### 2. 便携包（需本机已装 Java）

```bash
bash scripts/package-release.sh
unzip release/bms-1.0.0.zip
cd bms-1.0.0
```

| 系统 | 怎么用 |
| --- | --- |
| Linux | `./start.sh` |
| macOS | 双击 `start.command`，或 `./start.sh` |
| Windows | 双击 `start.bat` |

### 3. 原生包（捆绑 JRE，不必装 Java）

合并到默认分支且便携包冒烟通过后，GitHub Actions 自动发布 GitHub Release（也可在 Actions 里手动 `workflow_dispatch`）。分别在 Ubuntu / Windows / macOS 生成：

- `bms-1.0.0-linux-x64.zip` → `bin/bms`
- `bms-1.0.0-windows-x64.zip` → 双击 `bms.exe`
- `bms-1.0.0-macos-arm64.zip` → Apple Silicon（M 系列），双击 `bms.app`
- `bms-1.0.0-macos-x64.zip` → Intel Mac，双击 `bms.app`

浏览器访问 `http://127.0.0.1:8084`。默认账号 `admin / admin123`。

十二套系统可同时启动：OMS 8081 / WMS 8082 / TMS 8083 / BMS 8084 / SAP 8085 / OA 8086 / SRM 8087 / BOM 8088 / INV 8089 / IR 8090 / CRM 8091 / DMS 8092。

