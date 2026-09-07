<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="合同号/名称" clearable @keyup.enter="reload" @clear="reload" />
        <el-select v-model="query.direction" placeholder="方向" clearable @change="reload" style="width: 120px">
          <el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.partnerCode" placeholder="结算对象" clearable filterable @change="reload">
          <el-option v-for="o in options.partner" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable @change="reload" style="width: 120px">
          <el-option v-for="s in ['DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED']" :key="s" :label="STATUS[s]" :value="s" />
        </el-select>
        <el-button type="primary" @click="reload"><el-icon><Search /></el-icon>查询</el-button>
        <el-button type="success" v-if="canWrite()" @click="openForm()"><el-icon><Plus /></el-icon>新建合同</el-button>
        <el-button v-if="canWrite()" @click="expire">处理到期合同</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="contractNo" label="合同号" width="140">
          <template #default="{ row }"><el-link type="primary" @click="$router.push(`/contract/detail/${row.id}`)">{{ row.contractNo }}</el-link></template>
        </el-table-column>
        <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="direction" label="方向" width="80"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
        <el-table-column prop="partnerCode" label="结算对象" width="150"><template #default="{ row }">{{ partnerName(row.partnerCode) }}</template></el-table-column>
        <el-table-column prop="startDate" label="生效日" width="110" />
        <el-table-column prop="endDate" label="失效日" width="110" />
        <el-table-column prop="settleCycle" label="结算周期" width="90"><template #default="{ row }">{{ CYCLE[row.settleCycle] || row.settleCycle }}</template></el-table-column>
        <el-table-column prop="taxRate" label="税率" width="70"><template #default="{ row }">{{ (row.taxRate * 100).toFixed(0) }}%</template></el-table-column>
        <el-table-column prop="paymentDays" label="账期(天)" width="80" />
        <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push(`/contract/detail/${row.id}`)">费率</el-button>
            <template v-if="canWrite()">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="act(contract.activate, row, '激活')">激活</el-button>
              <el-button v-if="row.status === 'ACTIVE'" link type="danger" size="small" @click="act(contract.terminate, row, '终止')">终止</el-button>
              <el-button v-if="['TERMINATED', 'EXPIRED'].includes(row.status)" link type="warning" size="small" @click="act(contract.reopen, row, '重新打开')">重开</el-button>
              <el-popconfirm v-if="row.status === 'DRAFT'" title="确认删除?" @confirm="remove(row)">
                <template #reference><el-button link type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50, 100]" @change="load" />
      </div>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑合同' : '新建合同'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="合同号" prop="contractNo"><el-input v-model="form.contractNo" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="方向" prop="direction">
              <el-select v-model="form.direction" style="width: 100%" @change="form.partnerCode = ''"><el-option v-for="o in DIRECTIONS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结算对象" prop="partnerCode">
              <el-select v-model="form.partnerCode" filterable style="width: 100%">
                <el-option v-for="o in partnerOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="生效日" prop="startDate"><el-date-picker v-model="form.startDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="失效日" prop="endDate"><el-date-picker v-model="form.endDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="结算周期"><el-select v-model="form.settleCycle" style="width: 100%"><el-option v-for="(l, v) in CYCLE" :key="v" :label="l" :value="v" /></el-select></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="币种"><el-input v-model="form.currency" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税率"><el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="账期(天)"><el-input-number v-model="form.paymentDays" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
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
import { contract } from '../../api'
import { useOptions, DIRECTIONS } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'

const STATUS = { DRAFT: '草稿', ACTIVE: '生效', EXPIRED: '已过期', TERMINATED: '已终止' }
const CYCLE = { MONTHLY: '月结', HALF_MONTH: '半月结', WEEKLY: '周结' }
const { options } = useOptions(['partner'])
const partnerName = (code) => options.value.partner?.find((p) => p.value === code)?.label || code
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, keyword: '', direction: '', partnerCode: '', status: '' })

async function load() {
  loading.value = true
  try {
    const p = await contract.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
function reload() { query.current = 1; load() }

async function act(fn, row, name) {
  await fn(row.id)
  ElMessage.success(`${name}成功`)
  load()
}
async function remove(row) {
  await contract.remove(row.id)
  ElMessage.success('已删除')
  load()
}
async function expire() {
  const n = await contract.expire()
  ElMessage.success(`已将 ${n} 份到期合同标记为过期`)
  load()
}

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = ref({})
const rules = {
  contractNo: [{ required: true, message: '必填', trigger: 'blur' }],
  name: [{ required: true, message: '必填', trigger: 'blur' }],
  direction: [{ required: true, message: '必填', trigger: 'change' }],
  partnerCode: [{ required: true, message: '必填', trigger: 'change' }],
  startDate: [{ required: true, message: '必填', trigger: 'change' }],
  endDate: [{ required: true, message: '必填', trigger: 'change' }]
}
const partnerOptions = computed(() => (options.value.partner || []).filter((p) => (form.value.direction === 'AR' ? p.type === 'CUSTOMER' : p.type !== 'CUSTOMER')))
function openForm(row) {
  form.value = row ? { ...row } : { direction: 'AR', settleCycle: 'MONTHLY', currency: 'CNY', taxRate: 0.06, paymentDays: 30 }
  visible.value = true
}
async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) await contract.update(form.value.id, form.value)
    else await contract.create(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
