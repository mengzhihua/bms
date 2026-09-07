<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="费用号/单据号/合同号" clearable @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.direction" placeholder="方向" clearable @change="reload" style="width: 110px">
          <el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.partnerCode" placeholder="结算对象" clearable filterable @change="reload">
          <el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.chargeItemCode" placeholder="费用项目" clearable filterable @change="reload" style="width: 160px">
          <el-option v-for="o in options.chargeItem" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable @change="reload" style="width: 110px">
          <el-option v-for="s in ['NEW', 'STATEMENTED', 'SETTLED', 'CANCELLED']" :key="s" :value="s"><StatusTag :value="s" /></el-option>
        </el-select>
        <el-select v-model="query.source" placeholder="来源" clearable @change="reload" style="width: 100px">
          <el-option v-for="s in ['AUTO', 'MANUAL', 'ADJUST']" :key="s" :value="s"><StatusTag :value="s" /></el-option>
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="业务日期起" end-placeholder="止" @change="reload" style="width: 240px" />
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="success" v-if="canWrite()" @click="openManual"><el-icon><Plus /></el-icon>手工费用</el-button>
        <el-button @click="downloadCsv('/billing/fee/export', params(), 'fees.csv')">导出 CSV</el-button>
      </div>
      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="6"><div class="stat"><div class="label">筛选结果条数</div><div class="value">{{ sum.cnt ?? sum.CNT ?? 0 }}</div></div></el-col>
        <el-col :span="6"><div class="stat"><div class="label">不含税金额</div><div class="value">{{ money(sum.amount ?? sum.AMOUNT) }}</div></div></el-col>
        <el-col :span="6"><div class="stat"><div class="label">税额</div><div class="value">{{ money(sum.tax_amount ?? sum.TAX_AMOUNT) }}</div></div></el-col>
        <el-col :span="6"><div class="stat"><div class="label">含税金额</div><div class="value" style="color: #409eff">{{ money(sum.total_amount ?? sum.TOTAL_AMOUNT) }}</div></div></el-col>
      </el-row>
      <FeeTable :fees="rows" :show-summary="false" @changed="load" />
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" title="手工录入费用" width="640px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="方向" required><el-select v-model="form.direction" style="width: 100%" @change="form.partnerCode = ''"><el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="结算对象" required><el-select v-model="form.partnerCode" filterable style="width: 100%"><el-option v-for="o in partnerOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用项目" required><el-select v-model="form.chargeItemCode" filterable style="width: 100%" @change="onItem"><el-option v-for="o in options.chargeItem" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务日期" required><el-date-picker v-model="form.bizDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="不含税金额" required><el-input-number v-model="form.amount" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税率"><el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务类型"><el-select v-model="form.bizType" clearable style="width: 100%"><el-option v-for="o in BIZ_TYPES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="仓库"><el-select v-model="form.warehouseCode" clearable style="width: 100%"><el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关联单据号"><el-input v-model="form.docNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数量"><el-input-number v-model="form.qty" :min="0" :precision="3" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注" required><el-input v-model="form.remark" /></el-form-item></el-col>
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
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { fee, downloadCsv } from '../../api'
import { useOptions, DIRECTIONS, BIZ_TYPES, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import FeeTable from './FeeTable.vue'
import { canWrite } from '../../auth'
import { today } from '../../utils'

const route = useRoute()
const { options } = useOptions(['partner', 'chargeItem', 'warehouse'])
const rows = ref([])
const total = ref(0)
const sum = ref({})
const loading = ref(false)
const range = ref([])
const query = reactive({ current: 1, size: 20, keyword: '', direction: route.query.direction || '', partnerCode: route.query.partnerCode || '', chargeItemCode: '', status: route.query.status || '', source: '' })
const params = () => ({ ...query, from: range.value?.[0] || undefined, to: range.value?.[1] || undefined })

async function load() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([fee.page(params()), fee.summary(params())])
    rows.value = p.records
    total.value = p.total
    sum.value = s || {}
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const partnerOptions = computed(() => (options.value.partner || []).filter((p) => (form.value.direction === 'AR' ? p.type === 'CUSTOMER' : p.type !== 'CUSTOMER')))
function openManual() {
  form.value = { direction: 'AR', bizDate: today(), amount: 0, taxRate: 0.06, qty: 1 }
  visible.value = true
}
function onItem(code) {
  const it = options.value.chargeItem?.find((c) => c.value === code)
  if (it && it.taxRate != null) form.value.taxRate = Number(it.taxRate)
}
async function save() {
  if (!form.value.partnerCode || !form.value.chargeItemCode) return ElMessage.warning('请填写必填项')
  saving.value = true
  try {
    await fee.manual(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
