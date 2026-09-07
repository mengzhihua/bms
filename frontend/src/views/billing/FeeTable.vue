<template>
  <el-table :data="fees" border stripe size="small" :show-summary="showSummary" :summary-method="summary">
    <el-table-column prop="feeNo" label="费用号" width="150" />
    <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
    <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
    <el-table-column prop="partnerCode" label="结算对象" width="100" />
    <el-table-column v-if="showDoc" prop="docNo" label="单据号" width="150">
      <template #default="{ row }"><el-link v-if="row.docNo" type="primary" @click="$router.push(`/billing/doc/${row.docNo}`)">{{ row.docNo }}</el-link></template>
    </el-table-column>
    <el-table-column prop="contractNo" label="合同" width="120" />
    <el-table-column prop="chargeItemCode" label="费用项目" width="150" />
    <el-table-column prop="bizType" label="业务" width="70"><template #default="{ row }"><StatusTag v-if="row.bizType" :value="row.bizType" /></template></el-table-column>
    <el-table-column prop="bizDate" label="业务日期" width="100" />
    <el-table-column label="数量" width="110"><template #default="{ row }">{{ Number(row.qty) }} {{ labelOf(UNITS, row.unit) }}</template></el-table-column>
    <el-table-column prop="unitPrice" label="单价" width="80" align="right" />
    <el-table-column prop="amount" label="不含税" width="90" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
    <el-table-column prop="taxAmount" label="税额" width="80" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
    <el-table-column prop="totalAmount" label="含税" width="90" align="right"><template #default="{ row }"><b>{{ money(row.totalAmount) }}</b></template></el-table-column>
    <el-table-column prop="source" label="来源" width="70"><template #default="{ row }"><StatusTag :value="row.source" /></template></el-table-column>
    <el-table-column prop="statementNo" label="对账单" width="150">
      <template #default="{ row }"><el-link v-if="row.statementNo" type="primary" @click="$router.push(`/settlement/statement/${row.statementNo}`)">{{ row.statementNo }}</el-link></template>
    </el-table-column>
    <el-table-column prop="calcDetail" label="计算过程" min-width="220" show-overflow-tooltip />
    <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
    <el-table-column v-if="canWrite()" label="操作" width="70" fixed="right">
      <template #default="{ row }">
        <el-button v-if="row.status === 'NEW'" link type="danger" size="small" @click="cancel(row)">作废</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { fee } from '../../api'
import { UNITS, labelOf, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'

defineProps({ fees: { type: Array, default: () => [] }, showDoc: { type: Boolean, default: true }, showSummary: { type: Boolean, default: true } })
const emit = defineEmits(['changed'])

function summary({ columns, data }) {
  return columns.map((c, i) => {
    if (i === 0) return '合计'
    if (['amount', 'taxAmount', 'totalAmount'].includes(c.property)) {
      return money(data.filter((r) => r.status !== 'CANCELLED').reduce((a, r) => a + Number(r[c.property] || 0), 0))
    }
    return ''
  })
}
async function cancel(row) {
  const { value } = await ElMessageBox.prompt('请输入作废原因', '作废费用')
  await fee.cancel(row.feeNo, value)
  ElMessage.success('已作废')
  emit('changed')
}
</script>
