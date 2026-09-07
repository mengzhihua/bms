<template>
  <div class="page" v-loading="loading">
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <h3 style="margin: 0">业务单据 {{ d.docNo }} <StatusTag :value="d.billStatus" /> <StatusTag :value="d.source" /> <StatusTag :value="d.bizType" /></h3>
        <div>
          <template v-if="canWrite()">
            <el-button v-if="d.billStatus !== 'IGNORED'" type="primary" size="small" @click="bill">{{ d.billStatus === 'BILLED' ? '重新计费' : '计费' }}</el-button>
          </template>
          <el-button size="small" @click="$router.back()">返回</el-button>
        </div>
      </div>
      <el-alert v-if="d.failReason" type="error" :closable="false" show-icon :title="d.failReason" style="margin-bottom: 12px" />
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="外部单号">{{ d.extRef }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ d.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="承运/供应商">{{ d.supplierCode }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ d.warehouseCode }}</el-descriptions-item>
        <el-descriptions-item label="业务日期">{{ d.bizDate }}</el-descriptions-item>
        <el-descriptions-item label="单数 / 行数">{{ d.orders }} / {{ d.lines }}</el-descriptions-item>
        <el-descriptions-item label="件 / 箱 / 托">{{ d.qty }} / {{ d.boxes }} / {{ d.pallets }}</el-descriptions-item>
        <el-descriptions-item label="重量 / 体积 / 里程">{{ d.weight }}kg / {{ d.volume }}m³ / {{ d.distance }}km</el-descriptions-item>
        <el-descriptions-item label="天数">{{ d.days }}</el-descriptions-item>
        <el-descriptions-item label="起 → 终">{{ d.origin }} → {{ d.destination }}</el-descriptions-item>
        <el-descriptions-item label="应收合计"><b style="color: #67c23a">{{ money(d.arAmount) }}</b></el-descriptions-item>
        <el-descriptions-item label="应付合计"><b style="color: #e6a23c">{{ money(d.apAmount) }}</b></el-descriptions-item>
        <el-descriptions-item label="备注" :span="4">{{ d.remark }}</el-descriptions-item>
      </el-descriptions>
    </div>
    <div class="card" style="margin-top: 12px">
      <h3 style="margin: 0 0 12px">费用明细 ({{ fees.length }})</h3>
      <FeeTable :fees="fees" @changed="load" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { doc } from '../../api'
import { money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'
import FeeTable from './FeeTable.vue'
import { canWrite } from '../../auth'

const route = useRoute()
const d = ref({})
const fees = ref([])
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const r = await doc.get(route.params.docNo)
    d.value = r.doc
    fees.value = r.fees
  } finally {
    loading.value = false
  }
}
async function bill() {
  const r = await doc.bill(d.value.docNo)
  if (r.billStatus === 'FAILED') ElMessage.warning(`计费失败: ${r.failReason}`)
  else ElMessage.success('计费成功')
  load()
}
onMounted(load)
</script>
