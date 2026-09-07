<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="收付款单号/银行流水" clearable @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.direction" placeholder="方向" clearable @change="reload" style="width: 110px"><el-option label="收款 AR" value="AR" /><el-option label="付款 AP" value="AP" /></el-select>
        <el-select v-model="query.partnerCode" placeholder="结算对象" clearable filterable @change="reload"><el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-checkbox v-model="query.unapplied" @change="reload">仅看有未核销余额</el-checkbox>
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="success" v-if="canWrite()" @click="openCreate"><el-icon><Plus /></el-icon>登记收付款</el-button>
      </div>
      <el-alert v-if="target" type="success" :closable="false" show-icon style="margin-bottom: 12px">
        正在为对账单 <b>{{ target }}</b> 核销：选择一笔有余额的收付款点击“核销”，或先登记新收付款。
      </el-alert>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 16px">
              <b>核销明细</b>
              <el-table :data="detail[row.paymentNo] || []" size="small" border style="margin-top: 6px">
                <el-table-column prop="statementNo" label="对账单" width="160"><template #default="s"><el-link type="primary" @click="$router.push(`/settlement/statement/${s.row.statementNo}`)">{{ s.row.statementNo }}</el-link></template></el-table-column>
                <el-table-column prop="amount" label="核销金额" width="120" align="right"><template #default="s">{{ money(s.row.amount) }}</template></el-table-column>
                <el-table-column prop="operator" label="操作人" width="100" />
                <el-table-column prop="createdAt" label="时间" width="160"><template #default="s">{{ fmt(s.row.createdAt) }}</template></el-table-column>
                <el-table-column v-if="canWrite()" label="操作" width="90"><template #default="s"><el-button link type="danger" size="small" @click="unapply(s.row)">取消核销</el-button></template></el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="paymentNo" label="收付款单号" width="150" />
        <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><el-tag size="small" :type="row.direction === 'AR' ? 'success' : 'warning'">{{ row.direction === 'AR' ? '收款' : '付款' }}</el-tag></template></el-table-column>
        <el-table-column prop="partnerCode" label="结算对象" width="150"><template #default="{ row }">{{ partnerName(row.partnerCode) }}</template></el-table-column>
        <el-table-column prop="payDate" label="日期" width="110" />
        <el-table-column prop="method" label="方式" width="80"><template #default="{ row }"><StatusTag :value="row.method" /></template></el-table-column>
        <el-table-column prop="bankRef" label="银行流水号" width="160" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column prop="appliedAmount" label="已核销" width="120" align="right"><template #default="{ row }">{{ money(row.appliedAmount) }}</template></el-table-column>
        <el-table-column label="未核销" width="120" align="right"><template #default="{ row }"><b :style="{ color: remain(row) > 0 ? '#e6a23c' : '#67c23a' }">{{ money(remain(row)) }}</b></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="canWrite()" label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="remain(row) > 0" link type="primary" size="small" @click="openApply(row)">核销</el-button>
            <el-popconfirm v-if="!Number(row.appliedAmount)" title="确认删除?" @confirm="remove(row)"><template #reference><el-button link type="danger" size="small">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" title="登记收付款" width="520px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="方向" required><el-radio-group v-model="form.direction" @change="form.partnerCode = ''"><el-radio-button value="AR">收款(客户)</el-radio-button><el-radio-button value="AP">付款(承运商/供应商)</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="结算对象" required><el-select v-model="form.partnerCode" filterable style="width: 100%"><el-option v-for="o in partnerOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="金额" required><el-input-number v-model="form.amount" :min="0.01" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.payDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="方式"><el-select v-model="form.method" style="width: 100%"><el-option label="转账" value="TRANSFER" /><el-option label="支票" value="CHECK" /><el-option label="现金" value="CASH" /><el-option label="抵扣" value="OFFSET" /></el-select></el-form-item>
        <el-form-item label="银行流水号"><el-input v-model="form.bankRef" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="applyVisible" title="核销对账单" width="520px" destroy-on-close>
      <el-form :model="apply" label-width="100px">
        <el-form-item label="收付款单">{{ apply.paymentNo }}（未核销 {{ money(apply.remain) }}）</el-form-item>
        <el-form-item label="对账单" required>
          <el-select v-model="apply.statementNo" filterable style="width: 100%" placeholder="该对象已确认未结清的对账单">
            <el-option v-for="s in candidates" :key="s.statementNo" :label="`${s.statementNo}  未收付 ${money(Number(s.totalAmount) - Number(s.paidAmount))}`" :value="s.statementNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="核销金额"><el-input-number v-model="apply.amount" :min="0.01" :precision="2" style="width: 100%" placeholder="留空 = 自动取二者较小值" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doApply">核销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { payment, statement } from '../../api'
import { useOptions, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'
import { fmt, today } from '../../utils'

const route = useRoute()
const target = route.query.statementNo || ''
const { options } = useOptions(['partner'])
const partnerName = (code) => options.value.partner?.find((p) => p.value === code)?.label || code
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detail = reactive({})
const query = reactive({ current: 1, size: 20, keyword: '', direction: route.query.direction || '', partnerCode: route.query.partnerCode || '', unapplied: false })
const remain = (r) => Number(r.amount) - Number(r.appliedAmount || 0)

async function load() {
  loading.value = true
  try {
    const p = await payment.page({ ...query, unapplied: query.unapplied || undefined })
    rows.value = p.records
    total.value = p.total
    await Promise.all(rows.value.map(async (r) => { detail[r.paymentNo] = (await payment.get(r.paymentNo)).applies }))
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }
async function remove(row) {
  await payment.remove(row.paymentNo)
  ElMessage.success('已删除')
  load()
}
async function unapply(a) {
  await ElMessageBox.confirm('取消该笔核销？', '取消核销')
  await payment.unapply(a.id)
  ElMessage.success('已取消核销')
  load()
}

const visible = ref(false)
const saving = ref(false)
const form = ref({})
const partnerOptions = computed(() => (options.value.partner || []).filter((p) => (form.value.direction === 'AR' ? p.type === 'CUSTOMER' : p.type !== 'CUSTOMER')))
function openCreate() {
  form.value = { direction: route.query.direction || 'AR', partnerCode: route.query.partnerCode || '', amount: 0, payDate: today(), method: 'TRANSFER' }
  visible.value = true
}
async function save() {
  if (!form.value.partnerCode || !form.value.amount) return ElMessage.warning('请填写结算对象与金额')
  saving.value = true
  try {
    await payment.create(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const applyVisible = ref(false)
const apply = reactive({ paymentNo: '', statementNo: '', amount: undefined, remain: 0 })
const candidates = ref([])
async function openApply(row) {
  Object.assign(apply, { paymentNo: row.paymentNo, statementNo: target || '', amount: undefined, remain: remain(row) })
  const p = await statement.page({ current: 1, size: 200, direction: row.direction, partnerCode: row.partnerCode, status: 'CONFIRMED' })
  candidates.value = p.records
  applyVisible.value = true
}
async function doApply() {
  if (!apply.statementNo) return ElMessage.warning('请选择对账单')
  saving.value = true
  try {
    await payment.apply({ paymentNo: apply.paymentNo, statementNo: apply.statementNo, amount: apply.amount || undefined })
    ElMessage.success('核销成功')
    applyVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
