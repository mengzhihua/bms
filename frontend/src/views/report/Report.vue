<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="起" end-placeholder="止" @change="load" style="width: 260px" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-tabs v-model="tab" @tab-change="load">
        <el-tab-pane label="收入 / 成本 / 毛利" name="profit">
          <el-row :gutter="12" style="margin-bottom: 12px">
            <el-col :span="6"><div class="stat"><div class="label">应收收入</div><div class="value" style="color: #67c23a">{{ money(profit.revenue) }}</div></div></el-col>
            <el-col :span="6"><div class="stat"><div class="label">应付成本</div><div class="value" style="color: #e6a23c">{{ money(profit.cost) }}</div></div></el-col>
            <el-col :span="6"><div class="stat"><div class="label">毛利</div><div class="value" style="color: #409eff">{{ money(profit.grossProfit) }}</div></div></el-col>
            <el-col :span="6"><div class="stat"><div class="label">毛利率</div><div class="value">{{ profit.grossMargin ?? 0 }}%</div></div></el-col>
          </el-row>
          <el-table :data="profit.rows || []" border stripe size="small">
            <el-table-column label="结算对象" min-width="160"><template #default="{ row }">{{ g(row, 'partnerCode') }}</template></el-table-column>
            <el-table-column label="方向" width="80"><template #default="{ row }"><StatusTag :value="g(row, 'direction')" /></template></el-table-column>
            <el-table-column label="笔数" width="80" align="right"><template #default="{ row }">{{ g(row, 'cnt') }}</template></el-table-column>
            <el-table-column label="含税金额" width="140" align="right"><template #default="{ row }">{{ money(g(row, 'totalAmount')) }}</template></el-table-column>
            <el-table-column label="占比" min-width="200">
              <template #default="{ row }"><el-progress :percentage="pct(row)" :color="g(row, 'direction') === 'AR' ? '#67c23a' : '#e6a23c'" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用汇总(对象×项目)" name="summary">
          <el-table :data="summary" border stripe size="small">
            <el-table-column label="方向" width="80"><template #default="{ row }"><StatusTag :value="g(row, 'direction')" /></template></el-table-column>
            <el-table-column label="结算对象" min-width="140"><template #default="{ row }">{{ g(row, 'partnerCode') }}</template></el-table-column>
            <el-table-column label="费用项目" min-width="150"><template #default="{ row }">{{ g(row, 'chargeItemCode') }}</template></el-table-column>
            <el-table-column label="笔数" width="80" align="right"><template #default="{ row }">{{ g(row, 'cnt') }}</template></el-table-column>
            <el-table-column label="不含税" width="120" align="right"><template #default="{ row }">{{ money(g(row, 'amount')) }}</template></el-table-column>
            <el-table-column label="税额" width="110" align="right"><template #default="{ row }">{{ money(g(row, 'taxAmount')) }}</template></el-table-column>
            <el-table-column label="含税" width="130" align="right"><template #default="{ row }"><b>{{ money(g(row, 'totalAmount')) }}</b></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="费用项目构成" name="items">
          <el-table :data="items" border stripe size="small">
            <el-table-column label="方向" width="80"><template #default="{ row }"><StatusTag :value="g(row, 'direction')" /></template></el-table-column>
            <el-table-column label="费用项目" min-width="160"><template #default="{ row }">{{ g(row, 'chargeItemCode') }}</template></el-table-column>
            <el-table-column label="笔数" width="80" align="right"><template #default="{ row }">{{ g(row, 'cnt') }}</template></el-table-column>
            <el-table-column label="含税金额" width="140" align="right"><template #default="{ row }">{{ money(g(row, 'totalAmount')) }}</template></el-table-column>
            <el-table-column label="占比" min-width="200">
              <template #default="{ row }"><el-progress :percentage="itemPct(row)" :color="g(row, 'direction') === 'AR' ? '#67c23a' : '#e6a23c'" /></template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="账龄分析" name="aging">
          <el-radio-group v-model="agingDir" size="small" @change="load" style="margin-bottom: 10px">
            <el-radio-button value="AR">应收账龄</el-radio-button>
            <el-radio-button value="AP">应付账龄</el-radio-button>
          </el-radio-group>
          <el-table :data="aging" border stripe size="small" show-summary :summary-method="agingSum">
            <el-table-column prop="partnerCode" label="结算对象" min-width="160" />
            <el-table-column prop="count" label="对账单数" width="90" align="right" />
            <el-table-column prop="notDue" label="未到期" width="120" align="right"><template #default="{ row }">{{ money(row.notDue) }}</template></el-table-column>
            <el-table-column prop="d1_30" label="1-30天" width="120" align="right"><template #default="{ row }">{{ money(row.d1_30) }}</template></el-table-column>
            <el-table-column prop="d31_60" label="31-60天" width="120" align="right"><template #default="{ row }">{{ money(row.d31_60) }}</template></el-table-column>
            <el-table-column prop="d61_90" label="61-90天" width="120" align="right"><template #default="{ row }">{{ money(row.d61_90) }}</template></el-table-column>
            <el-table-column prop="d90p" label="90天以上" width="120" align="right"><template #default="{ row }"><span style="color: #f56c6c">{{ money(row.d90p) }}</span></template></el-table-column>
            <el-table-column prop="total" label="未结清合计" width="130" align="right"><template #default="{ row }"><b>{{ money(row.total) }}</b></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="结算对象台账" name="ledger">
          <div class="toolbar">
            <el-select v-model="ledgerPartner" filterable placeholder="选择结算对象" @change="load"><el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" /></el-select>
            <el-select v-model="ledgerDir" clearable placeholder="方向" @change="load" style="width: 110px"><el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </div>
          <el-table :data="ledger" border stripe size="small" show-summary :summary-method="ledgerSum">
            <el-table-column prop="statementNo" label="对账单号" width="150"><template #default="{ row }"><el-link type="primary" @click="$router.push(`/settlement/statement/${row.statementNo}`)">{{ row.statementNo }}</el-link></template></el-table-column>
            <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
            <el-table-column label="账期" width="200"><template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template></el-table-column>
            <el-table-column prop="totalAmount" label="含税合计" width="120" align="right"><template #default="{ row }">{{ money(row.totalAmount) }}</template></el-table-column>
            <el-table-column prop="invoicedAmount" label="已开票" width="120" align="right"><template #default="{ row }">{{ money(row.invoicedAmount) }}</template></el-table-column>
            <el-table-column prop="paidAmount" label="已收付" width="120" align="right"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
            <el-table-column label="未收付" width="120" align="right"><template #default="{ row }"><b>{{ money(Number(row.totalAmount) - Number(row.paidAmount)) }}</b></template></el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="110" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { report } from '../../api'
import { useOptions, DIRECTIONS, money, g } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const { options } = useOptions(['partner'])
const tab = ref('profit')
const d = new Date()
const pad = (n) => String(n).padStart(2, '0')
const range = ref([`${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`, `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`])
const profit = ref({})
const summary = ref([])
const items = ref([])
const aging = ref([])
const agingDir = ref('AR')
const ledger = ref([])
const ledgerPartner = ref('')
const ledgerDir = ref('')

const params = () => ({ from: range.value?.[0], to: range.value?.[1] })
const pct = (row) => {
  const base = g(row, 'direction') === 'AR' ? Number(profit.value.revenue || 0) : Number(profit.value.cost || 0)
  return base ? Math.round((Number(g(row, 'totalAmount')) / base) * 100) : 0
}
const itemPct = (row) => {
  const base = items.value.filter((r) => g(r, 'direction') === g(row, 'direction')).reduce((a, r) => a + Number(g(r, 'totalAmount') || 0), 0)
  return base ? Math.round((Number(g(row, 'totalAmount')) / base) * 100) : 0
}
const sumCols = (keys) => ({ columns, data }) => columns.map((c, i) => (i === 0 ? '合计' : keys.includes(c.property) ? money(data.reduce((a, r) => a + Number(r[c.property] || 0), 0)) : ''))
const agingSum = sumCols(['notDue', 'd1_30', 'd31_60', 'd61_90', 'd90p', 'total'])
const ledgerSum = sumCols(['totalAmount', 'invoicedAmount', 'paidAmount'])

async function load() {
  if (tab.value === 'profit') profit.value = await report.profit(params())
  else if (tab.value === 'summary') summary.value = await report.feeSummary(params())
  else if (tab.value === 'items') items.value = await report.feeByItem(params())
  else if (tab.value === 'aging') aging.value = await report.aging(agingDir.value)
  else if (tab.value === 'ledger') ledger.value = ledgerPartner.value ? await report.ledger({ partnerCode: ledgerPartner.value, direction: ledgerDir.value || undefined }) : []
}
onMounted(load)
</script>
