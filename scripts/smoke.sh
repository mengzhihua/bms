#!/usr/bin/env bash
# BMS 端到端冒烟：登录 -> 基础数据/合同 -> WMS/TMS 单据推送(Open API) 自动计费 -> 手工费用 -> 对账单生成/确认 -> 开票 -> 收款核销 -> 报表
# 用法: scripts/smoke.sh [BASE_URL] ; 环境变量 BMS_ADMIN_PASSWORD / BMS_OPEN_API_KEY 需与后端一致
set -euo pipefail
BASE="${1:-http://localhost:8080}/api"
J='Content-Type: application/json'
PASS="${BMS_ADMIN_PASSWORD:?需设置 BMS_ADMIN_PASSWORD}"
API_KEY="${BMS_OPEN_API_KEY:?需设置 BMS_OPEN_API_KEY}"
need(){ command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need curl; need jq
TOKEN=""
call(){ local out; out=$(curl -s -X "$1" "$BASE$2" -H "$J" -H "Authorization: Bearer $TOKEN" ${3:+-d "$3"}); [ "$(echo "$out"|jq -r .code)" = "0" ] || { echo "FAIL $1 $2 -> $out"; exit 1; }; echo "$out"|jq -c .data; }
open(){ local out; out=$(curl -s -X "$1" "$BASE/open$2" -H "$J" -H "X-Api-Key: $API_KEY" ${3:+-d "$3"}); [ "$(echo "$out"|jq -r .code)" = "0" ] || { echo "FAIL OPEN $1 $2 -> $out"; exit 1; }; echo "$out"|jq -c .data; }
TS=$(date +%s); TODAY=$(date +%F)

echo "== 1 auth"
test "$(curl -s "$BASE/contract/page" | jq -r .code)" = "401"
TOKEN=$(call POST /auth/login "{\"username\":\"admin\",\"password\":\"$PASS\"}" | jq -r .token); test -n "$TOKEN"
test "$(call GET /auth/me | jq -r .role)" = "ADMIN"

echo "== 2 master data & contracts"
test "$(call GET '/basic/partner/page?size=100' | jq '.records|length')" -ge 5
test "$(call GET '/basic/warehouse/list' | jq 'length')" -ge 3
test "$(call GET '/basic/charge-item/list' | jq 'length')" -ge 8
test "$(call GET '/contract/page?size=100&status=ACTIVE' | jq '.records|length')" -ge 3
CID=$(call GET '/contract/page?size=100&keyword=AR-2024-001' | jq '.records[0].id')
test "$(call GET "/contract/$CID/rules" | jq 'length')" -ge 3

echo "== 3 wms/tms push (open api) -> auto billing"
test "$(curl -s -X POST "$BASE/open/wms/docs" -H "$J" -H 'X-Api-Key: wrong' -d '[]' | jq -r .code)" = "401"
W=$(open POST /wms/docs "[{\"extRef\":\"OUT$TS\",\"bizType\":\"OUTBOUND\",\"customerCode\":\"CUST-001\",\"warehouseCode\":\"WH-SH\",\"bizDate\":\"$TODAY\",\"orders\":1,\"lines\":3,\"qty\":25,\"boxes\":2}]")
test "$(echo "$W"|jq -r '.[0].billStatus')" = "BILLED"; DOC1=$(echo "$W"|jq -r '.[0].docNo')
AR1=$(echo "$W"|jq -r '.[0].arAmount'); echo "outbound AR=$AR1"; test "$(echo "$AR1 > 0"|bc)" = 1
# 幂等：同 source+extRef 再推返回同一单
test "$(open POST /wms/docs "[{\"extRef\":\"OUT$TS\",\"bizType\":\"OUTBOUND\",\"customerCode\":\"CUST-001\",\"bizDate\":\"$TODAY\"}]" | jq -r '.[0].docNo')" = "$DOC1"
T=$(open POST /tms/docs "[{\"extRef\":\"TR$TS\",\"customerCode\":\"CUST-001\",\"supplierCode\":\"SF\",\"bizDate\":\"$TODAY\",\"weight\":12.5,\"origin\":\"上海\",\"destination\":\"杭州\"}]")
test "$(echo "$T"|jq -r '.[0].billStatus')" = "BILLED"; DOC2=$(echo "$T"|jq -r '.[0].docNo')
test "$(echo "$(echo "$T"|jq -r '.[0].arAmount') > 0"|bc)" = 1; test "$(echo "$(echo "$T"|jq -r '.[0].apAmount') > 0"|bc)" = 1
test "$(open GET "/docs/TMS/TR$TS" | jq -r .doc.docNo)" = "$DOC2"
test "$(call GET "/billing/doc/$DOC2" | jq '.fees|length')" -ge 2
# 无合同客户 -> FAILED
F=$(open POST /wms/docs "[{\"extRef\":\"NC$TS\",\"bizType\":\"OUTBOUND\",\"customerCode\":\"CUST-003\",\"bizDate\":\"$TODAY\",\"qty\":1}]")
test "$(echo "$F"|jq -r '.[0].billStatus')" = "FAILED"
test "$(call GET '/integration/log/page?size=5' | jq '.records|length')" -ge 1

echo "== 4 manual fee & fee list"
MF=$(call POST /billing/fee/manual "{\"direction\":\"AR\",\"partnerCode\":\"CUST-001\",\"chargeItemCode\":\"OTHER\",\"amount\":100,\"taxRate\":0.06,\"bizDate\":\"$TODAY\",\"remark\":\"smoke 手工费\"}")
test "$(echo "$(echo "$MF"|jq -r .totalAmount) == 106"|bc)" = 1; MFN=$(echo "$MF"|jq -r .feeNo)
test "$(call GET "/billing/fee/page?partnerCode=CUST-001&status=NEW&size=100" | jq '.records|length')" -ge 3

echo "== 5 statement generate -> adjust -> confirm"
S=$(call POST /settlement/statement/generate "{\"direction\":\"AR\",\"partnerCode\":\"CUST-001\",\"periodStart\":\"$TODAY\",\"periodEnd\":\"$TODAY\"}")
SN=$(echo "$S"|jq -r .statementNo); test "$(echo "$S"|jq -r .status)" = "DRAFT"; TOT0=$(echo "$S"|jq -r .totalAmount)
call DELETE "/settlement/statement/$SN/fees/$MFN" >/dev/null
test "$(call GET "/billing/fee/$MFN" | jq -r .status)" = "NEW"
S=$(call POST "/settlement/statement/$SN/adjust" "{\"direction\":\"AR\",\"partnerCode\":\"CUST-001\",\"chargeItemCode\":\"OTHER\",\"amount\":-10,\"taxRate\":0,\"remark\":\"折让\"}")
TOT=$(echo "$S"|jq -r .totalAmount); test "$(echo "$TOT == $TOT0 - 106 - 10"|bc)" = 1
test "$(call POST "/settlement/statement/$SN/confirm" | jq -r .status)" = "CONFIRMED"
test "$(call GET "/settlement/statement/$SN" | jq '.logs|length')" -ge 2

echo "== 6 invoice -> payment -> apply -> settled"
INV=$(call POST /settlement/invoice "{\"statementNo\":\"$SN\",\"invoiceType\":\"SPECIAL\",\"invoiceCode\":\"INV$TS\",\"totalAmount\":$TOT}")
test "$(echo "$INV"|jq -r .status)" = "ISSUED"
P=$(call POST /settlement/payment "{\"direction\":\"AR\",\"partnerCode\":\"CUST-001\",\"amount\":$TOT,\"payDate\":\"$TODAY\",\"method\":\"TRANSFER\"}")
PN=$(echo "$P"|jq -r .paymentNo)
P=$(call POST /settlement/payment/apply "{\"paymentNo\":\"$PN\",\"statementNo\":\"$SN\"}")
test "$(echo "$(echo "$P"|jq -r .appliedAmount) == $(echo "$P"|jq -r .amount)"|bc)" = 1
test "$(call GET "/settlement/statement/$SN" | jq -r .statement.status)" = "SETTLED"
test "$(call GET "/settlement/statement/$SN" | jq -r '.fees[0].status')" = "SETTLED"

echo "== 7 reports"
D=$(call GET /report/dashboard); test "$(echo "$D"|jq -r .monthAr)" != "null"
test "$(call GET "/report/fee-summary?from=$TODAY&to=$TODAY" | jq 'length')" -ge 1
test "$(call GET "/report/profit?from=$TODAY&to=$TODAY" | jq -r .grossProfit)" != "null"
call GET '/report/aging?direction=AR' >/dev/null
test "$(call GET '/report/partner-ledger?partnerCode=CUST-001' | jq 'length')" -ge 1
curl -sf "$BASE/settlement/statement/$SN/export" -H "Authorization: Bearer $TOKEN" | head -1 | grep -q "feeNo\|费用"

echo "SMOKE OK: doc=$DOC1,$DOC2 statement=$SN payment=$PN"
