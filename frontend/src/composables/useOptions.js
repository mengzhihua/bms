import { ref } from 'vue'
import { basic } from '../api'

/** 页面级一次性加载基础数据下拉项 */
export function useOptions(kinds) {
  const options = ref({})
  const loaders = {
    partner: async () => (await basic.partner.list()).map((p) => ({ label: `${p.code} ${p.name}`, value: p.code, type: p.type })),
    customer: async () => (await basic.partner.list()).filter((p) => p.type === 'CUSTOMER').map((p) => ({ label: `${p.code} ${p.name}`, value: p.code })),
    supplier: async () => (await basic.partner.list()).filter((p) => p.type !== 'CUSTOMER').map((p) => ({ label: `${p.code} ${p.name}`, value: p.code, type: p.type })),
    warehouse: async () => (await basic.warehouse.list()).map((w) => ({ label: `${w.code} ${w.name}`, value: w.code })),
    chargeItem: async () => (await basic.chargeItem.list()).map((c) => ({ label: `${c.code} ${c.name}`, value: c.code, direction: c.direction, taxRate: c.defaultTaxRate }))
  }
  async function reload() {
    const out = {}
    await Promise.all(kinds.map(async (k) => { out[k] = await loaders[k]() }))
    options.value = out
  }
  reload()
  return { options, reload }
}

export const statusCol = { prop: 'status', label: '状态', type: 'status', width: 80, default: 1 }

export const DIRECTIONS = [{ label: '应收 AR', value: 'AR' }, { label: '应付 AP', value: 'AP' }]
export const BIZ_TYPES = [
  { label: '入库', value: 'INBOUND' }, { label: '出库', value: 'OUTBOUND' }, { label: '仓储', value: 'STORAGE' },
  { label: '运输', value: 'TRANSPORT' }, { label: '增值服务', value: 'VAS' }, { label: '退货', value: 'RETURN' }
]
export const UNITS = [
  { label: '单', value: 'ORDER' }, { label: '行', value: 'LINE' }, { label: '件', value: 'PIECE' }, { label: '箱', value: 'BOX' },
  { label: '托', value: 'PALLET' }, { label: '重量(kg)', value: 'WEIGHT' }, { label: '体积(m³)', value: 'VOLUME' }, { label: '里程(km)', value: 'DISTANCE' },
  { label: '托·天', value: 'PALLET_DAY' }, { label: '方·天', value: 'VOLUME_DAY' }, { label: '件·天', value: 'PIECE_DAY' }
]
export const PRICE_MODES = [
  { label: '固定', value: 'FIXED' }, { label: '单价', value: 'UNIT' }, { label: '阶梯(整段)', value: 'TIERED' }, { label: '累进(分段)', value: 'PROGRESSIVE' }, { label: '首重续重', value: 'FIRST_EXTRA' }
]
export const labelOf = (list, v) => list.find((o) => o.value === v)?.label ?? v
export const money = (v) => (v === null || v === undefined ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
/** 兼容 H2(大写列名) / MySQL(原样) 的 Map 结果取值 */
export const g = (row, k) => (row ? row[k] ?? row[k.toUpperCase()] ?? row[k.toLowerCase()] : undefined)
