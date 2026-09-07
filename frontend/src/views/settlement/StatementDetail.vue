<template>
  <div class="page" v-loading="loading">
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <h3 style="margin: 0">对账单 {{ s.statementNo }} <StatusTag :value="s.status" /> <StatusTag :value="s.direction" /></h3>
        <div>
          <template v-if="canWrite()">
            <el-button v-if="editable" type="success" size="small" @click="act(statement.confirm, '确认')">确认对账单</el-button>
            <el-button v-if="editable" size="small" @click="openAdjust">添加调整</el-button>
            <el-button v-if="['DRAFT', 'CONFIRMED'].includes(s.status)" type="warning" size="small" @click="withReason('争议原因', (r) => statement.dispute(no, r))">标记争议</el-button>
            <el-button v-if="['CONFIRMED', 'SETTLED'].includes(s.status) && remainInvoice > 0" type="primary" size="small" @click="openInvoice">开票</el-button>
            <el-button v-if="s.status === 'CONFIRMED' && remainPay > 0" type="primary" size="small" @click="$router.push({ path: '/settlement/payment', query: { statementNo: no, direction: s.direction, partnerCode: s.partnerCode } })">去核销</el-button>
            <el-button v-if="!['SETTLED', 'CANCELLED'].includes(s.status)" type="danger" size="small" @click="withReason('作废原因', (r) => statement.cancel(no, r))">作废</el-button>
          </template>
          <el-button size="small" @click="downloadCsv(`/settlement/statement/${no}/export`, {}, `${no}.csv`)">导出明细</el-button>
          <el-button size="small" @click="$router.back()">返回</el-button>
        </div>
      </div>
      <el-alert v-if="s.disputeReason" type="warning" :closable="false" show-icon :title="'争议：' + s.disputeReason" style="margin-bottom: 12px" />
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="结算对象">{{ s.partnerCode }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ s.periodStart }} ~ {{ s.periodEnd }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ s.dueDate }}</el-descriptions-item>
        <el-descriptions-item label="费用笔数">{{ s.feeCount }}</el-descriptions-item>
        <el-descriptions-item label="不含税">{{ money(s.amount) }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ money(s.taxAmount) }}</el-descriptions-item>
        <el-descriptions-item label="含税合计"><b style="color: #409eff">{{ money(s.totalAmount) }}</b></el-descriptions-item>
        <el-descriptions-item label="调整金额">{{ money(s.adjustAmount) }}</el-descriptions-item>
        <el-descriptions-item label="已开票 / 未开票">{{ money(s.invoicedAmount) }} / {{ money(remainInvoice) }}</el-descriptions-item>
        <el-descriptions-item label="已收付 / 未收付">{{ money(s.paidAmount) }} / <b :style="{ color: remainPay > 0 ? '#f56c6c' : '#67c23a' }">{{ money(remainPay) }}</b></el-descriptions-item>
        <el-descriptions-item label="确认人 / 时间">{{ s.confirmedBy }} {{ fmt(s.confirmedAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ s.remark }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card" style="margin-top: 12px">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`费用明细 (${fees.length})`" name="fees">
          <el-table :data="fees" border stripe size="small" show-summary :summary-method="summary">
            <el-table-column prop="feeNo" label="费用号" width="150" />
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
            <el-table-column prop="docNo" label="单据号" width="150"><template #default="{ row }"><el-link v-if="row.docNo" type="primary" @click="$router.push(`/billing/doc/${row.docNo}`)">{{ row.docNo }}</el-link></template></el-table-column>
            <el-table-column prop="bizType" label="业务" width="70"><template #default="{ row }"><StatusTag v-if="row.bizType" :value="row.bizType" /></template></el-table-column>
            <el-table-column prop="chargeItemCode" label="费用项目" width="150" />
            <el-table-column prop="bizDate" label="业务日期" width="100" />
            <el-table-column label="数量" width="110"><template #default="{ row }">{{ Number(row.qty) }} {{ labelOf(UNITS, row.unit) }}</template></el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="80" align="right" />
            <el-table-column prop="amount" label="不含税" width="90" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column prop="taxAmount" label="税额" width="80" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
            <el-table-column prop="totalAmount" label="含税" width="90" align="right"><template #default="{ row }"><b>{{ money(row.totalAmount) }}</b></template></el-table-column>
            <el-table-column prop="source" label="来源" width="70"><template #default="{ row }"><StatusTag :value="row.source" /></template></el-table-column>
            <el-table-column prop="calcDetail" label="计算过程" min-width="200" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
            <el-table-column v-if="canWrite() && editable" label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'CANCELLED'" link type="danger" size="small" @click="removeFee(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`发票 (${invoices.length})`" name="invoices">
          <el-table :data="invoices" border stripe size="small">
            <el-table-column prop="invoiceNo" label="系统票号" width="150" />
            <el-table-column prop="invoiceCode" label="发票号码" width="160" />
            <el-table-column prop="invoiceType" label="类型" width="80"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
            <el-table-column prop="status" label="状态" width="80"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
            <el-table-column prop="invoiceDate" label="开票日期" width="110" />
            <el-table-column prop="amount" label="不含税" width="100" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column prop="taxAmount" label="税额" width="100" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
            <el-table-column prop="totalAmount" label="含税" width="110" align="right"><template #default="{ row }"><b>{{ money(row.totalAmount) }}</b></template></el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" />
            <el-table-column v-if="canWrite()" label="操作" width="70"><template #default="{ row }"><el-button v-if="row.status === 'ISSUED'" link type="danger" size="small" @click="withReason('作废原因', (r) => invoice.cancel(row.invoiceNo, r))">作废</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`核销记录 (${applies.length})`" name="applies">
          <el-table :data="applies" border stripe size="small">
            <el-table-column prop="paymentNo" label="收付款单号" width="150" />
            <el-table-column prop="amount" label="核销金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="createdAt" label="时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
            <el-table-column v-if="canWrite()" label="操作" width="90"><template #default="{ row }"><el-button link type="danger" size="small" @click="unapply(row)">取消核销</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`操作日志 (${logs.length})`" name="logs">
          <el-timeline>
            <el-timeline-item v-for="l in logs" :key="l.id" :timestamp="fmt(l.createdAt)" placement="top">
              <b>{{ l.action }}</b> <StatusTag v-if="l.fromStatus" :value="l.fromStatus" /> <span v-if="l.fromStatus">→</span> <StatusTag v-if="l.toStatus" :value="l.toStatus" />
              <span style="color: #909399; margin-left: 8px">{{ l.operator }} {{ l.remark }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="adjVisible" title="添加调整费用（负数为折让）" width="520px" destroy-on-close>
      <el-form :model="adj" label-width="100px">
        <el-form-item label="费用项目" required><el-select v-model="adj.chargeItemCode" filterable style="width: 100%"><el-option v-for="o in options.chargeItem" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="不含税金额" required><el-input-number v-model="adj.amount" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="税率"><el-input-number v-model="adj.taxRate" :min="0" :max="1" :step="0.01" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="原因" required><el-input v-model="adj.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjVisible = false">取消</el-button>
        <el-button type="primary" @click="doAdjust">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invVisible" title="登记开票" width="520px" destroy-on-close>
      <el-form :model="inv" label-width="100px">
        <el-form-item label="发票类型"><el-select v-model="inv.invoiceType" style="width: 100%"><el-option label="增值税专用发票" value="SPECIAL" /><el-option label="增值税普通发票" value="NORMAL" /><el-option label="电子发票" value="ELECTRONIC" /></el-select></el-form-item>
        <el-form-item label="发票号码"><el-input v-model="inv.invoiceCode" /></el-form-item>
        <el-form-item label="开票日期"><el-date-picker v-model="inv.invoiceDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="含税金额" required><el-input-number v-model="inv.totalAmount" :min="0.01" :max="remainInvoice" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="inv.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invVisible = false">取消</el-button>
        <el-button type="primary" @click="doInvoice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { statement, invoice, payment, downloadCsv } from '../../api'
import { useOptions, UNITS, labelOf, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'
import { fmt, today } from '../../utils'

const route = useRoute()
const no = route.params.statementNo
const { options } = useOptions(['chargeItem'])
const s = ref({})
const fees = ref([])
const invoices = ref([])
const applies = ref([])
const logs = ref([])
const loading = ref(false)
const tab = ref('fees')
const editable = computed(() => ['DRAFT', 'DISPUTED'].includes(s.value.status))
const remainInvoice = computed(() => Number(s.value.totalAmount || 0) - Number(s.value.invoicedAmount || 0))
const remainPay = computed(() => Number(s.value.totalAmount || 0) - Number(s.value.paidAmount || 0))

async function load() {
  loading.value = true
  try {
    const r = await statement.get(no)
    s.value = r.statement
    fees.value = r.fees
    invoices.value = r.invoices
    applies.value = r.applies
    logs.value = r.logs
  } finally {
    loading.value = false
  }
}
function summary({ columns, data }) {
  return columns.map((c, i) => {
    if (i === 0) return '合计'
    if (['amount', 'taxAmount', 'totalAmount'].includes(c.property)) return money(data.filter((r) => r.status !== 'CANCELLED').reduce((a, r) => a + Number(r[c.property] || 0), 0))
    return ''
  })
}
async function act(fn, name) {
  await fn(no)
  ElMessage.success(`${name}成功`)
  load()
}
async function withReason(title, fn) {
  const { value } = await ElMessageBox.prompt('请输入原因', title, { inputValidator: (v) => (v && v.trim() ? true : '原因必填') })
  await fn(value)
  ElMessage.success('操作成功')
  load()
}
async function removeFee(row) {
  await ElMessageBox.confirm(`移除费用 ${row.feeNo}？普通费用退回“未对账”，调整费用作废。`, '移除费用')
  await statement.removeFee(no, row.feeNo)
  ElMessage.success('已移除')
  load()
}
async function unapply(row) {
  await ElMessageBox.confirm('取消该笔核销？', '取消核销')
  await payment.unapply(row.id)
  ElMessage.success('已取消核销')
  load()
}

const adjVisible = ref(false)
const adj = reactive({ chargeItemCode: 'OTHER', amount: 0, taxRate: 0, remark: '' })
function openAdjust() { Object.assign(adj, { chargeItemCode: 'OTHER', amount: 0, taxRate: 0, remark: '' }); adjVisible.value = true }
async function doAdjust() {
  if (!adj.amount || !adj.remark) return ElMessage.warning('金额和原因必填')
  await statement.adjust(no, { ...adj })
  ElMessage.success('调整已添加')
  adjVisible.value = false
  load()
}

const invVisible = ref(false)
const inv = reactive({ invoiceType: 'SPECIAL', invoiceCode: '', invoiceDate: today(), totalAmount: 0, remark: '' })
function openInvoice() { Object.assign(inv, { invoiceType: 'SPECIAL', invoiceCode: '', invoiceDate: today(), totalAmount: remainInvoice.value, remark: '' }); invVisible.value = true }
async function doInvoice() {
  await invoice.issue({ statementNo: no, ...inv })
  ElMessage.success('开票已登记')
  invVisible.value = false
  tab.value = 'invoices'
  load()
}
onMounted(load)
</script>
