<template>
  <el-tag :type="meta.type" size="small" effect="light">{{ meta.text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const DICT = {
  // 合同
  DRAFT: ['草稿', 'info'],
  ACTIVE: ['生效', 'success'],
  EXPIRED: ['已过期', 'warning'],
  TERMINATED: ['已终止', 'danger'],
  // 单据计费
  PENDING: ['待计费', 'info'],
  BILLED: ['已计费', 'success'],
  FAILED: ['计费失败', 'danger'],
  IGNORED: ['已忽略', 'warning'],
  // 费用
  NEW: ['未对账', 'info'],
  STATEMENTED: ['已对账', 'primary'],
  SETTLED: ['已结清', 'success'],
  CANCELLED: ['已作废', 'danger'],
  // 对账单
  CONFIRMED: ['已确认', 'primary'],
  DISPUTED: ['争议中', 'warning'],
  // 发票
  ISSUED: ['已开票', 'success'],
  // 方向 / 类型
  AR: ['应收', 'success'],
  AP: ['应付', 'warning'],
  CUSTOMER: ['客户', 'success'],
  CARRIER: ['承运商', 'warning'],
  SUPPLIER: ['供应商', 'primary'],
  // 来源
  AUTO: ['自动', 'success'],
  MANUAL: ['手工', 'info'],
  IMPORT: ['导入', 'primary'],
  ADJUST: ['调整', 'warning'],
  WMS: ['WMS', 'primary'],
  TMS: ['TMS', 'warning'],
  OMS: ['OMS', 'success'],
  IN: ['入站', 'success'],
  OUT: ['出站', 'primary'],
  // 业务类型
  INBOUND: ['入库', 'primary'],
  OUTBOUND: ['出库', 'success'],
  STORAGE: ['仓储', 'info'],
  TRANSPORT: ['运输', 'warning'],
  VAS: ['增值', 'info'],
  RETURN: ['退货', 'danger'],
  // 计价
  FIXED: ['固定', 'info'],
  UNIT: ['单价', 'primary'],
  TIERED: ['阶梯', 'warning'],
  PROGRESSIVE: ['累进', 'success'],
  FIRST_EXTRA: ['首重续重', 'warning'],
  // 发票类型 / 支付方式
  SPECIAL: ['专票', 'primary'],
  NORMAL: ['普票', 'info'],
  ELECTRONIC: ['电子票', 'success'],
  TRANSFER: ['转账', 'primary'],
  CHECK: ['支票', 'info'],
  CASH: ['现金', 'warning'],
  OFFSET: ['抵扣', 'info']
}

const props = defineProps({ value: String })
const meta = computed(() => {
  const d = DICT[props.value]
  return d ? { text: d[0], type: d[1] } : { text: props.value, type: 'info' }
})
</script>
