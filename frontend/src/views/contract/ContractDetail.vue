<template>
  <div class="page" v-loading="loading">
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <h3 style="margin: 0">合同 {{ c.contractNo }} <StatusTag :value="c.status" /> <StatusTag :value="c.direction" /></h3>
        <div>
          <template v-if="canWrite()">
            <el-button v-if="c.status === 'DRAFT'" type="success" size="small" @click="act(contract.activate, '激活')">激活</el-button>
            <el-button v-if="c.status === 'ACTIVE'" type="danger" size="small" @click="act(contract.terminate, '终止')">终止</el-button>
            <el-button v-if="['TERMINATED', 'EXPIRED'].includes(c.status)" type="warning" size="small" @click="act(contract.reopen, '重开')">重开</el-button>
          </template>
          <el-button size="small" @click="$router.back()">返回</el-button>
        </div>
      </div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="合同名称">{{ c.name }}</el-descriptions-item>
        <el-descriptions-item label="结算对象">{{ c.partnerCode }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ c.startDate }} ~ {{ c.endDate }}</el-descriptions-item>
        <el-descriptions-item label="结算周期">{{ c.settleCycle }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ c.currency }}</el-descriptions-item>
        <el-descriptions-item label="税率">{{ c.taxRate }}</el-descriptions-item>
        <el-descriptions-item label="账期">{{ c.paymentDays }} 天</el-descriptions-item>
        <el-descriptions-item label="备注">{{ c.remark }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="card" style="margin-top: 12px">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <h3 style="margin: 0">费率规则 ({{ rules.length }})</h3>
        <el-button v-if="canWrite()" type="primary" size="small" @click="openRule()"><el-icon><Plus /></el-icon>新增费率</el-button>
      </div>
      <el-alert v-if="c.status === 'ACTIVE'" type="warning" :closable="false" show-icon style="margin-bottom: 8px">合同已生效，修改费率将影响后续计费与重算，请谨慎操作。</el-alert>
      <el-table :data="rules" border stripe size="small">
        <el-table-column prop="rule.bizType" label="业务类型" width="90"><template #default="{ row }"><StatusTag :value="row.rule.bizType" /></template></el-table-column>
        <el-table-column prop="rule.chargeItemCode" label="费用项目" width="170"><template #default="{ row }">{{ itemName(row.rule.chargeItemCode) }}</template></el-table-column>
        <el-table-column prop="rule.warehouseCode" label="仓库" width="90"><template #default="{ row }">{{ row.rule.warehouseCode || '全部' }}</template></el-table-column>
        <el-table-column prop="rule.unit" label="计费单位" width="90"><template #default="{ row }">{{ labelOf(UNITS, row.rule.unit) }}</template></el-table-column>
        <el-table-column prop="rule.priceMode" label="计价方式" width="90"><template #default="{ row }"><StatusTag :value="row.rule.priceMode" /></template></el-table-column>
        <el-table-column label="价格 / 阶梯" min-width="260">
          <template #default="{ row }">
            <span v-if="row.rule.priceMode === 'FIRST_EXTRA'">首重 {{ row.tiers[0]?.toQty }} / {{ row.tiers[0]?.price }}，续重 {{ row.rule.unitPrice }}</span>
            <span v-else-if="['FIXED', 'UNIT'].includes(row.rule.priceMode)">{{ row.rule.unitPrice }}</span>
            <span v-else>{{ row.tiers.map((t) => `${t.fromQty}~${t.toQty ?? '∞'}: ${t.price}`).join(' | ') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rule.minCharge" label="最低" width="70" />
        <el-table-column prop="rule.maxCharge" label="最高" width="70" />
        <el-table-column prop="rule.priority" label="优先级" width="70" />
        <el-table-column prop="rule.status" label="状态" width="70"><template #default="{ row }"><el-tag size="small" :type="row.rule.status === 1 ? 'success' : 'info'">{{ row.rule.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="rule.remark" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right" v-if="canWrite()">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRule(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="removeRule(row)"><template #reference><el-button link type="danger" size="small">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="ruleVisible" :title="rule.id ? '编辑费率' : '新增费率'" width="720px" destroy-on-close>
      <el-form :model="rule" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="业务类型" required><el-select v-model="rule.bizType" style="width: 100%"><el-option v-for="o in BIZ_TYPES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用项目" required><el-select v-model="rule.chargeItemCode" filterable style="width: 100%"><el-option v-for="o in options.chargeItem" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="仓库"><el-select v-model="rule.warehouseCode" clearable placeholder="全部仓库" style="width: 100%"><el-option v-for="o in options.warehouse" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计费单位" required><el-select v-model="rule.unit" style="width: 100%"><el-option v-for="o in UNITS" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计价方式" required><el-select v-model="rule.priceMode" style="width: 100%"><el-option v-for="o in PRICE_MODES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12" v-if="['FIXED', 'UNIT', 'FIRST_EXTRA'].includes(rule.priceMode)"><el-form-item :label="rule.priceMode === 'FIXED' ? '固定金额' : rule.priceMode === 'FIRST_EXTRA' ? '续重单价' : '单价'" required><el-input-number v-model="rule.unitPrice" :min="0" :precision="4" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最低收费"><el-input-number v-model="rule.minCharge" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最高收费"><el-input-number v-model="rule.maxCharge" :min="0" :precision="2" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="优先级"><el-input-number v-model="rule.priority" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-switch v-model="rule.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="说明"><el-input v-model="rule.remark" /></el-form-item></el-col>
        </el-row>
        <template v-if="rule.priceMode === 'FIRST_EXTRA'">
          <el-divider content-position="left">首重：数量以内按整段金额，超出按续重单价</el-divider>
          <el-form-item label="首重数量" required><el-input-number v-model="firstTier.toQty" :min="0.001" :precision="3" /></el-form-item>
          <el-form-item label="首重金额" required><el-input-number v-model="firstTier.price" :min="0" :precision="4" /></el-form-item>
        </template>
        <template v-else-if="['TIERED', 'PROGRESSIVE'].includes(rule.priceMode)">
          <el-divider content-position="left">阶梯区间（{{ rule.priceMode === 'TIERED' ? '按数量落入的区间价格整段计价' : '逐段累进；首段 0~X 视为首重/首件整段价' }}）</el-divider>
          <el-table :data="tiers" size="small" border>
            <el-table-column label="从 (>)" width="160"><template #default="{ row }"><el-input-number v-model="row.fromQty" :min="0" :precision="3" size="small" style="width: 100%" /></template></el-table-column>
            <el-table-column label="到 (≤，空=无上限)" width="180"><template #default="{ row }"><el-input-number v-model="row.toQty" :min="0" :precision="3" size="small" style="width: 100%" /></template></el-table-column>
            <el-table-column label="价格" width="160"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="4" size="small" style="width: 100%" /></template></el-table-column>
            <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" size="small" @click="tiers.splice($index, 1)">删除</el-button></template></el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="addTier">添加区间</el-button>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { contract } from '../../api'
import { useOptions, BIZ_TYPES, UNITS, PRICE_MODES, labelOf } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import { canWrite } from '../../auth'

const route = useRoute()
const id = route.params.id
const { options } = useOptions(['chargeItem', 'warehouse'])
const itemName = (code) => options.value.chargeItem?.find((p) => p.value === code)?.label || code
const c = ref({})
const rules = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [a, b] = await Promise.all([contract.get(id), contract.rules(id)])
    c.value = a
    rules.value = b
  } finally {
    loading.value = false
  }
}
async function act(fn, name) {
  await fn(id)
  ElMessage.success(`${name}成功`)
  load()
}

const ruleVisible = ref(false)
const saving = ref(false)
const rule = ref({})
const tiers = ref([])
const firstTier = ref({ fromQty: 0, toQty: 1, price: 0 })
function openRule(row) {
  rule.value = row ? { ...row.rule } : { bizType: 'OUTBOUND', unit: 'PIECE', priceMode: 'UNIT', unitPrice: 0, minCharge: 0, maxCharge: 0, priority: 1, status: 1 }
  tiers.value = row ? row.tiers.map((t) => ({ ...t })) : []
  const head = tiers.value[0]
  firstTier.value = head ? { fromQty: 0, toQty: head.toQty ?? 1, price: head.price ?? 0 } : { fromQty: 0, toQty: 1, price: 0 }
  ruleVisible.value = true
}
function addTier() {
  const last = tiers.value[tiers.value.length - 1]
  tiers.value.push({ fromQty: last ? last.toQty ?? 0 : 0, toQty: null, price: 0 })
}
async function saveRule() {
  saving.value = true
  try {
    const tierRows = rule.value.priceMode === 'FIRST_EXTRA'
      ? [{ fromQty: 0, toQty: firstTier.value.toQty, price: firstTier.value.price }]
      : ['TIERED', 'PROGRESSIVE'].includes(rule.value.priceMode) ? tiers.value : []
    await contract.saveRule(id, { rule: rule.value, tiers: tierRows })
    ElMessage.success('保存成功')
    ruleVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
async function removeRule(row) {
  await contract.removeRule(id, row.rule.id)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>
