<template>
  <CrudPage title="仓库" :api="basic.warehouse" :columns="columns" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '仓库编码', required: true, readonlyOnEdit: true, width: 110 },
  { prop: 'name', label: '仓库名称', required: true, minWidth: 160 },
  { prop: 'city', label: '城市', width: 100 },
  { prop: 'address', label: '地址', minWidth: 200, span: 24 },
  statusCol
]
</script>
