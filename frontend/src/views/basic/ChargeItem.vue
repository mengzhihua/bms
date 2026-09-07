<template>
  <CrudPage title="费用项目" :api="basic.chargeItem" :columns="columns" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 150 },
  { prop: 'name', label: '名称', required: true, minWidth: 140 },
  { prop: 'category', label: '类别', type: 'select', required: true, filter: true, width: 90, default: 'STORAGE', options: [{ label: '仓储', value: 'STORAGE' }, { label: '操作', value: 'HANDLING' }, { label: '运输', value: 'TRANSPORT' }, { label: '增值', value: 'VAS' }, { label: '其他', value: 'OTHER' }] },
  { prop: 'direction', label: '适用方向', type: 'select', required: true, width: 90, default: 'BOTH', options: [{ label: '应收+应付', value: 'BOTH' }, { label: '仅应收', value: 'AR' }, { label: '仅应付', value: 'AP' }] },
  { prop: 'defaultTaxRate', label: '默认税率', type: 'number', precision: 2, width: 90, default: 0.06 },
  statusCol
]
</script>
