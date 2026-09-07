<template>
  <div class="page">
    <div class="card">
      <h3 style="margin: 0 0 12px">业务单据批量导入（CSV）</h3>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <p>列：extRef, bizType, customerCode, supplierCode, warehouseCode, bizDate, orders, lines, qty, boxes, pallets, weight, volume, distance, days, origin, destination, remark</p>
        <p>bizType 取值 INBOUND / OUTBOUND / STORAGE / TRANSPORT / VAS / RETURN；同一 extRef 重复导入会被跳过（幂等）；导入后可自动按合同费率计费。</p>
      </el-alert>
      <div class="toolbar">
        <el-button @click="downloadCsv('/billing/doc/import-template', {}, 'biz_doc_template.csv')">下载模板</el-button>
        <el-switch v-model="autoBill" active-text="导入后自动计费" />
        <el-upload v-if="canWrite()" :show-file-list="false" :auto-upload="false" accept=".csv" :on-change="onImport">
          <el-button type="primary" :loading="importing">选择 CSV 导入</el-button>
        </el-upload>
      </div>
      <template v-if="result">
        <el-divider content-position="left">导入结果</el-divider>
        <p>共 <b>{{ result.total }}</b> 行：成功 <b style="color: #67c23a">{{ result.success }}</b>，重复跳过 <b>{{ result.skipped }}</b>，失败 <b style="color: #f56c6c">{{ result.errors.length }}</b></p>
        <el-table v-if="result.errors.length" :data="result.errors.map((e) => ({ e }))" size="small" border>
          <el-table-column prop="e" label="错误" />
        </el-table>
        <el-button v-else type="primary" link @click="$router.push('/billing/doc')">查看业务单据 →</el-button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { doc, downloadCsv } from '../../api'
import { canWrite } from '../../auth'

const importing = ref(false)
const autoBill = ref(true)
const result = ref(null)

async function onImport(file) {
  importing.value = true
  try {
    result.value = await doc.importCsv(file.raw, autoBill.value)
    ElMessage.success('导入完成')
  } finally {
    importing.value = false
  }
}
</script>
