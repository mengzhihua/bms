<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="票号/发票号码/对账单号" clearable @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.direction" placeholder="方向" clearable @change="reload" style="width: 110px"><el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-select v-model="query.partnerCode" placeholder="结算对象" clearable filterable @change="reload"><el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-select v-model="query.status" placeholder="状态" clearable @change="reload" style="width: 110px"><el-option v-for="s in ['ISSUED', 'CANCELLED']" :key="s" :value="s"><StatusTag :value="s" /></el-option></el-select>
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">开票在对账单详情页（已确认/已结清状态）进行登记；此处查看与作废。</el-alert>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="invoiceNo" label="系统票号" width="150" />
        <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
        <el-table-column prop="partnerCode" label="结算对象" width="120" />
        <el-table-column prop="statementNo" label="对账单" width="150"><template #default="{ row }"><el-link type="primary" @click="$router.push(`/settlement/statement/${row.statementNo}`)">{{ row.statementNo }}</el-link></template></el-table-column>
        <el-table-column prop="invoiceType" label="类型" width="80"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="invoiceCode" label="发票号码" width="160" />
        <el-table-column prop="invoiceDate" label="开票日期" width="110" />
        <el-table-column prop="amount" label="不含税" width="110" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="100" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        <el-table-column prop="totalAmount" label="含税" width="120" align="right"><template #default="{ row }"><b>{{ money(row.totalAmount) }}</b></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="canWrite()" label="操作" width="70" fixed="right">
          <template #default="{ row }"><el-button v-if="row.status === 'ISSUED'" link type="danger" size="small" @click="cancel(row)">作废</el-button></template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { invoice } from '../../api'
import { useOptions, DIRECTIONS, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'

const { options } = useOptions(['partner'])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, keyword: '', direction: '', partnerCode: '', status: '' })
async function load() {
  loading.value = true
  try {
    const p = await invoice.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }
async function cancel(row) {
  const { value } = await ElMessageBox.prompt('请输入作废原因', '作废发票')
  await invoice.cancel(row.invoiceNo, value)
  ElMessage.success('已作废')
  load()
}
onMounted(load)
</script>
