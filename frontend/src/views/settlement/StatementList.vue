<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="对账单号" clearable @keyup.enter="reload" @clear="reload" style="width: 160px" />
        <el-select v-model="query.direction" placeholder="方向" clearable @change="reload" style="width: 110px">
          <el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.partnerCode" placeholder="结算对象" clearable filterable @change="reload">
          <el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable @change="reload" style="width: 110px">
          <el-option v-for="s in ['DRAFT', 'CONFIRMED', 'DISPUTED', 'SETTLED', 'CANCELLED']" :key="s" :value="s"><StatusTag :value="s" /></el-option>
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="账期起" end-placeholder="止" @change="reload" style="width: 240px" />
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="success" v-if="canWrite()" @click="openGen"><el-icon><Plus /></el-icon>生成对账单</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="statementNo" label="对账单号" width="150">
          <template #default="{ row }"><el-link type="primary" @click="$router.push(`/settlement/statement/${row.statementNo}`)">{{ row.statementNo }}</el-link></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
        <el-table-column prop="partnerCode" label="结算对象" width="150"><template #default="{ row }">{{ partnerName(row.partnerCode) }}</template></el-table-column>
        <el-table-column label="账期" width="200"><template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template></el-table-column>
        <el-table-column prop="feeCount" label="笔数" width="60" align="right" />
        <el-table-column prop="amount" label="不含税" width="100" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="90" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        <el-table-column prop="totalAmount" label="含税合计" width="110" align="right"><template #default="{ row }"><b>{{ money(row.totalAmount) }}</b></template></el-table-column>
        <el-table-column prop="adjustAmount" label="调整" width="80" align="right"><template #default="{ row }">{{ money(row.adjustAmount) }}</template></el-table-column>
        <el-table-column prop="invoicedAmount" label="已开票" width="100" align="right"><template #default="{ row }">{{ money(row.invoicedAmount) }}</template></el-table-column>
        <el-table-column prop="paidAmount" label="已收付" width="100" align="right"><template #default="{ row }">{{ money(row.paidAmount) }}</template></el-table-column>
        <el-table-column prop="dueDate" label="到期日" width="100">
          <template #default="{ row }"><span :style="{ color: overdue(row) ? '#f56c6c' : '' }">{{ row.dueDate }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" v-if="canWrite()">
          <template #default="{ row }">
            <el-button v-if="['DRAFT', 'DISPUTED'].includes(row.status)" link type="success" size="small" @click="act(statement.confirm, row, '确认')">确认</el-button>
            <el-button v-if="['DRAFT', 'CONFIRMED'].includes(row.status)" link type="warning" size="small" @click="withReason('争议原因', (r) => statement.dispute(row.statementNo, r))">争议</el-button>
            <el-button v-if="!['SETTLED', 'CANCELLED'].includes(row.status)" link type="danger" size="small" @click="withReason('作废原因', (r) => statement.cancel(row.statementNo, r))">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>

    <el-dialog v-model="genVisible" title="生成对账单" width="520px" destroy-on-close>
      <el-form :model="gen" label-width="100px">
        <el-form-item label="方向" required><el-radio-group v-model="gen.direction" @change="gen.partnerCode = ''"><el-radio-button v-for="o in DIRECTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="账期" required><el-date-picker v-model="gen.range" type="daterange" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结算对象">
          <el-select v-model="gen.partnerCode" filterable clearable placeholder="留空 = 批量生成所有有未对账费用的对象" style="width: 100%">
            <el-option v-for="o in genPartners" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" v-if="gen.partnerCode"><el-input v-model="gen.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doGen">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { statement } from '../../api'
import { useOptions, DIRECTIONS, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'
import { today } from '../../utils'

const route = useRoute()
const router = useRouter()
const { options } = useOptions(['partner'])
const partnerName = (code) => options.value.partner?.find((p) => p.value === code)?.label || code
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const range = ref([])
const query = reactive({ current: 1, size: 20, keyword: '', direction: route.query.direction || '', partnerCode: '', status: route.query.status || '' })
const overdue = (r) => r.status === 'CONFIRMED' && r.dueDate && r.dueDate < today()

async function load() {
  loading.value = true
  try {
    const p = await statement.page({ ...query, from: range.value?.[0] || undefined, to: range.value?.[1] || undefined })
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }
async function act(fn, row, name) {
  await fn(row.statementNo)
  ElMessage.success(`${name}成功`)
  load()
}
async function withReason(title, fn) {
  const { value } = await ElMessageBox.prompt('请输入原因', title, { inputValidator: (v) => (v && v.trim() ? true : '原因必填') })
  await fn(value)
  ElMessage.success('操作成功')
  load()
}

const genVisible = ref(false)
const saving = ref(false)
const gen = reactive({ direction: 'AR', range: [], partnerCode: '', remark: '' })
const genPartners = computed(() => (options.value.partner || []).filter((p) => (gen.direction === 'AR' ? p.type === 'CUSTOMER' : p.type !== 'CUSTOMER')))
function openGen() {
  const d = new Date()
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const fmtD = (x) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  Object.assign(gen, { direction: 'AR', range: [fmtD(first), fmtD(d)], partnerCode: '', remark: '' })
  genVisible.value = true
}
async function doGen() {
  if (!gen.range?.length) return ElMessage.warning('请选择账期')
  saving.value = true
  try {
    if (gen.partnerCode) {
      const s = await statement.generate({ direction: gen.direction, partnerCode: gen.partnerCode, periodStart: gen.range[0], periodEnd: gen.range[1], remark: gen.remark })
      ElMessage.success(`已生成 ${s.statementNo}`)
      genVisible.value = false
      router.push(`/settlement/statement/${s.statementNo}`)
    } else {
      const list = await statement.generateBatch({ direction: gen.direction, periodStart: gen.range[0], periodEnd: gen.range[1] })
      ElMessage.success(`已生成 ${list.length} 张对账单`)
      genVisible.value = false
      load()
    }
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
