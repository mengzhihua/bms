<template>
  <div class="page">
    <div class="card">
      <h3 style="margin: 0 0 8px">开放接口（Open API）</h3>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        所有 <code>/api/open/**</code> 接口通过请求头 <code>X-Api-Key</code> 鉴权（服务端环境变量 <code>BMS_OPEN_API_KEY</code>）。
        上游 WMS / TMS / OMS 推送业务单据后，BMS 按 <b>source + extRef</b> 幂等落库并立即按生效合同费率自动计费（应收 + 应付）。
        响应统一为 <code>{ code: 0, msg: 'success', data }</code>；非 0 表示业务失败。
      </el-alert>
      <el-table :data="apis" border size="small">
        <el-table-column prop="side" label="调用方" width="90"><template #default="{ row }"><StatusTag :value="row.side" /></template></el-table-column>
        <el-table-column prop="method" label="方法" width="70" />
        <el-table-column prop="path" label="路径" width="300"><template #default="{ row }"><code>{{ row.path }}</code></template></el-table-column>
        <el-table-column prop="desc" label="说明" min-width="220" />
        <el-table-column prop="body" label="请求体 / 参数" min-width="380"><template #default="{ row }"><code style="white-space: pre-wrap">{{ row.body }}</code></template></el-table-column>
      </el-table>
      <h4>单据字段说明</h4>
      <el-table :data="fields" border size="small">
        <el-table-column prop="name" label="字段" width="140"><template #default="{ row }"><code>{{ row.name }}</code></template></el-table-column>
        <el-table-column prop="desc" label="说明" min-width="300" />
        <el-table-column prop="unit" label="对应计费单位" width="220" />
      </el-table>
      <h4>示例</h4>
      <pre class="json">curl -X POST http://localhost:8080/api/open/wms/docs \
  -H 'X-Api-Key: $BMS_OPEN_API_KEY' -H 'Content-Type: application/json' \
  -d '[{"extRef":"OUT20240601001","bizType":"OUTBOUND","customerCode":"CUST-001","supplierCode":"SF",
        "warehouseCode":"WH-SH","bizDate":"2024-06-01","orders":1,"lines":3,"qty":25,"boxes":2,"weight":12.5}]'

# 返回：[{ extRef, docNo, billStatus: BILLED|FAILED, arAmount, apAmount, error }]</pre>
    </div>
  </div>
</template>

<script setup>
import StatusTag from '../../components/StatusTag.vue'

const body = '[{ extRef*, bizType*, customerCode*, supplierCode, warehouseCode, bizDate, orders, lines, qty, boxes, pallets, weight, volume, distance, days, origin, destination, remark }]'
const apis = [
  { side: 'WMS', method: 'POST', path: '/api/open/wms/docs', desc: '仓储作业单据批量推送：入库(INBOUND)/出库(OUTBOUND)/退货(RETURN)/仓储快照(STORAGE)/增值(VAS)', body },
  { side: 'TMS', method: 'POST', path: '/api/open/tms/docs', desc: '运单批量推送（bizType 默认 TRANSPORT；supplierCode 为承运商，用于生成应付）', body },
  { side: 'OMS', method: 'POST', path: '/api/open/oms/docs', desc: 'OMS 订单类单据批量推送（如订单服务费）', body },
  { side: 'WMS', method: 'GET', path: '/api/open/docs/{source}/{extRef}', desc: '按来源 + 外部单号查询单据计费结果与费用明细', body: '-' }
]
const fields = [
  { name: 'bizType', desc: 'INBOUND 入库 / OUTBOUND 出库 / STORAGE 仓储 / TRANSPORT 运输 / VAS 增值 / RETURN 退货；匹配合同费率的业务类型', unit: '-' },
  { name: 'customerCode', desc: '客户编码，用于匹配 AR 合同', unit: '-' },
  { name: 'supplierCode', desc: '承运商/供应商编码，可选；有值则同时匹配 AP 合同生成应付', unit: '-' },
  { name: 'orders / lines', desc: '单数（默认 1）/ 行数', unit: 'ORDER / LINE' },
  { name: 'qty / boxes / pallets', desc: '件数 / 箱数 / 托数', unit: 'PIECE / BOX / PALLET' },
  { name: 'weight / volume / distance', desc: '重量 kg / 体积 m³ / 里程 km', unit: 'WEIGHT / VOLUME / DISTANCE' },
  { name: 'days', desc: '仓储天数（默认 1），与托数/体积/件数相乘', unit: 'PALLET_DAY / VOLUME_DAY / PIECE_DAY' }
]
</script>

<style scoped>
.json { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 12px; overflow: auto; }
code { background: #f5f7fa; padding: 1px 4px; border-radius: 3px; font-size: 12px; }
</style>
