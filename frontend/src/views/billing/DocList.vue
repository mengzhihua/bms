<template>
  <div class="page">
    <div class="card">
      <el-radio-group v-model="query.billStatus" size="small" @change="reload" style="margin-bottom: 10px">
        <el-radio-button value="">全部 {{ sum }}</el-radio-button>
        <el-radio-button v-for="s in STATUSES" :key="s" :value="s">{{ LABEL[s] }} {{ counts[s] || 0 }}</el-radio-button>
      </el-radio-group>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="单据号/外部单号" clearable @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.source" placeholder="来源" clearable @change="reload" style="width: 110px">
          <el-option v-for="s in ['WMS', 'TMS', 'OMS', 'MANUAL', 'IMPORT']" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="query.bizType" placeholder="业务类型" clearable @change="reload" style="width: 120px">
          <el-option v-for="o in BIZ_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.customerCode" placeholder="客户" clearable filterable @change="reload">
          <el-option v-for="o in options.customer" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.warehouseCode" placeholder="仓库" clearable @change="reload" style="width: 130px">
          <el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="业务日期起" end-placeholder="止" @change="reload" style="width: 240px" />
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="success" v-if="canWrite()" @click="openCreate"><el-icon><Plus /></el-icon>手工录入</el-button>
        <el-button v-if="canWrite()" type="warning" @click="billPending">批量计费待处理</el-button>
        <el-button @click="downloadCsv('/billing/doc/export', params(), 'biz_docs.csv')">导出 CSV</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="docNo" label="单据号" width="160">
          <template #default="{ row }"><el-link type="primary" @click="$router.push(`/billing/doc/${row.docNo}`)">{{ row.docNo }}</el-link></template>
        </el-table-column>
        <el-table-column prop="billStatus" label="计费状态" width="90"><template #default="{ row }"><StatusTag :value="row.billStatus" /></template></el-table-column>
        <el-table-column prop="source" label="来源" width="70"><template #default="{ row }"><StatusTag :value="row.source" /></template></el-table-column>
        <el-table-column prop="extRef" label="外部单号" width="150" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" width="80"><template #default="{ row }"><StatusTag :value="row.bizType" /></template></el-table-column>
        <el-table-column prop="customerCode" label="客户" width="100" />
        <el-table-column prop="supplierCode" label="承运/供应商" width="100" />
        <el-table-column prop="warehouseCode" label="仓库" width="80" />
        <el-table-column prop="bizDate" label="业务日期" width="100" />
        <el-table-column label="数量" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ qtyText(row) }}</template></el-table-column>
        <el-table-column prop="arAmount" label="应收" width="90" align="right"><template #default="{ row }">{{ money(row.arAmount) }}</template></el-table-column>
        <el-table-column prop="apAmount" label="应付" width="90" align="right"><template #default="{ row }">{{ money(row.apAmount) }}</template></el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" v-if="canWrite()">
          <template #default="{ row }">
            <el-button v-if="row.billStatus !== 'IGNORED'" link type="primary" size="small" @click="act(doc.bill, row, row.billStatus === 'BILLED' ? '重算' : '计费')">{{ row.billStatus === 'BILLED' ? '重算' : '计费' }}</el-button>
            <el-button v-if="['PENDING', 'FAILED'].includes(row.billStatus)" link type="warning" size="small" @click="ignore(row)">忽略</el-button>
            <el-button v-if="row.billStatus === 'IGNORED'" link type="primary" size="small" @click="act(doc.reopen, row, '恢复')">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" title="手工录入业务单据" width="720px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="业务类型" required><el-select v-model="form.bizType" style="width: 100%"><el-option v-for="o in BIZ_TYPES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务日期" required><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户" required><el-select v-model="form.customerCode" filterable style="width: 100%"><el-option v-for="o in options.customer" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承运/供应商"><el-select v-model="form.supplierCode" filterable clearable style="width: 100%"><el-option v-for="o in options.supplier" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="仓库"><el-select v-model="form.warehouseCode" clearable style="width: 100%"><el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="外部单号"><el-input v-model="form.extRef" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单数"><el-input-number v-model="form.orders" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="行数"><el-input-number v-model="form.lines" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="件数"><el-input-number v-model="form.qty" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="箱数"><el-input-number v-model="form.boxes" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="托数"><el-input-number v-model="form.pallets" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="重量(kg)"><el-input-number v-model="form.weight" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="体积(m³)"><el-input-number v-model="form.volume" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="里程(km)"><el-input-number v-model="form.distance" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="天数"><el-input-number v-model="form.days" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="起点"><el-input v-model="form.origin" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="终点"><el-input v-model="form.destination" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="保存后计费"><el-switch v-model="autoBill" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { doc, downloadCsv } from '../../api'
import { useOptions, BIZ_TYPES, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'
import { today } from '../../utils'

const STATUSES = ['PENDING', 'BILLED', 'FAILED', 'IGNORED']
const LABEL = { PENDING: '待计费', BILLED: '已计费', FAILED: '计费失败', IGNORED: '已忽略' }
const route = useRoute()
const { options } = useOptions(['customer', 'supplier', 'warehouse'])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const counts = ref({})
const range = ref([])
const query = reactive({ current: 1, size: 20, keyword: '', billStatus: route.query.billStatus || '', source: '', bizType: '', customerCode: '', warehouseCode: '' })
const sum = computed(() => STATUSES.reduce((a, s) => a + (counts.value[s] || 0), 0))
const params = () => ({ ...query, from: range.value?.[0] || undefined, to: range.value?.[1] || undefined })

const qtyText = (r) => {
  const parts = []
  if (r.orders) parts.push(`${r.orders}单`)
  if (r.lines) parts.push(`${r.lines}行`)
  if (Number(r.qty)) parts.push(`${Number(r.qty)}件`)
  if (Number(r.boxes)) parts.push(`${Number(r.boxes)}箱`)
  if (Number(r.pallets)) parts.push(`${Number(r.pallets)}托`)
  if (Number(r.weight)) parts.push(`${Number(r.weight)}kg`)
  if (Number(r.volume)) parts.push(`${Number(r.volume)}m³`)
  if (Number(r.distance)) parts.push(`${Number(r.distance)}km`)
  if (r.days && r.days !== 1) parts.push(`${r.days}天`)
  return parts.join(' / ')
}

async function load() {
  loading.value = true
  try {
    const [p, c] = await Promise.all([doc.page(params()), doc.statusCount()])
    rows.value = p.records
    total.value = p.total
    counts.value = c
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }
async function act(fn, row, name) {
  const r = await fn(row.docNo)
  if (r.billStatus === 'FAILED') ElMessage.warning(`${name}失败: ${r.failReason}`)
  else ElMessage.success(`${name}成功`)
  load()
}
async function ignore(row) {
  const { value } = await ElMessageBox.prompt('请输入忽略原因', '忽略单据')
  await doc.ignore(row.docNo, value)
  ElMessage.success('已忽略')
  load()
}
async function billPending() {
  const n = await doc.billPending()
  ElMessage.success(`批量计费完成，成功 ${n} 单`)
  load()
}

const visible = ref(false)
const saving = ref(false)
const autoBill = ref(true)
const form = ref({})
function openCreate() {
  form.value = { bizType: 'OUTBOUND', bizDate: today(), orders: 1, lines: 0, qty: 0, boxes: 0, pallets: 0, weight: 0, volume: 0, distance: 0, days: 1 }
  visible.value = true
}
async function save() {
  if (!form.value.customerCode) return ElMessage.warning('请选择客户')
  saving.value = true
  try {
    const r = await doc.create(form.value, autoBill.value)
    if (r.billStatus === 'FAILED') ElMessage.warning(`已保存，但计费失败: ${r.failReason}`)
    else ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
