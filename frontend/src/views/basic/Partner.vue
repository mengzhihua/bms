<template>
  <CrudPage title="结算对象" :api="basic.partner" :columns="columns" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 110 },
  { prop: 'name', label: '名称', required: true, minWidth: 160 },
  { prop: 'type', label: '类型', type: 'select', required: true, filter: true, width: 90, default: 'CUSTOMER', options: [{ label: '客户', value: 'CUSTOMER' }, { label: '承运商', value: 'CARRIER' }, { label: '供应商', value: 'SUPPLIER' }] },
  { prop: 'contact', label: '联系人', width: 90 },
  { prop: 'phone', label: '电话', width: 130 },
  { prop: 'taxNo', label: '税号', width: 170 },
  { prop: 'bankAccount', label: '银行账户', hideInTable: true },
  { prop: 'address', label: '地址', hideInTable: true, span: 24 },
  { prop: 'settleCycle', label: '结算周期', type: 'select', width: 90, default: 'MONTHLY', options: [{ label: '月结', value: 'MONTHLY' }, { label: '半月结', value: 'HALF_MONTH' }, { label: '周结', value: 'WEEKLY' }] },
  { prop: 'creditLimit', label: '信用额度', type: 'number', precision: 2, width: 110 },
  statusCol
]
</script>
