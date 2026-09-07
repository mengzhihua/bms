<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="4" v-for="s in stats" :key="s.label">
        <div class="stat">
          <div class="label">{{ s.label }}</div>
          <div class="value" :style="{ color: s.color }">{{ s.value }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="margin-top: 12px">
      <h3 style="margin: 0 0 12px">待办事项</h3>
      <el-row :gutter="12">
        <el-col :span="4" v-for="t in todos" :key="t.label">
          <div class="todo" @click="$router.push(t.to)">
            <div class="todo-value" :style="{ color: t.color }">{{ t.value ?? 0 }}</div>
            <div class="todo-label">{{ t.label }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="14">
        <div class="card">
          <h3 style="margin: 0 0 12px">近 30 日应收 / 应付趋势</h3>
          <div class="bars">
            <div v-for="t in trend" :key="g(t, 'bizDate')" class="bar-col" :title="`${g(t, 'bizDate')} 应收 ${money(g(t, 'ar'))} / 应付 ${money(g(t, 'ap'))}`">
              <div class="bar-pair">
                <div class="bar ar" :style="{ height: barH(g(t, 'ar')) + 'px' }"></div>
                <div class="bar ap" :style="{ height: barH(g(t, 'ap')) + 'px' }"></div>
              </div>
              <div class="bar-day">{{ String(g(t, 'bizDate')).slice(5, 10) }}</div>
            </div>
            <el-empty v-if="!trend.length" description="暂无数据" :image-size="60" />
          </div>
          <div style="font-size: 12px; color: #909399; margin-top: 6px"><span class="dot ar"></span>应收 <span class="dot ap" style="margin-left: 12px"></span>应付</div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card">
          <h3 style="margin: 0 0 12px">本月结算对象 TOP</h3>
          <el-table :data="top" size="small" border max-height="260">
            <el-table-column label="结算对象" min-width="120"><template #default="{ row }">{{ g(row, 'partnerCode') }}</template></el-table-column>
            <el-table-column label="方向" width="70"><template #default="{ row }"><StatusTag :value="g(row, 'direction')" /></template></el-table-column>
            <el-table-column label="笔数" width="70" align="right"><template #default="{ row }">{{ g(row, 'cnt') }}</template></el-table-column>
            <el-table-column label="含税金额" width="120" align="right"><template #default="{ row }">{{ money(g(row, 'totalAmount')) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { report } from '../api'
import { money, g } from '../composables/useOptions'
import StatusTag from '../components/StatusTag.vue'

const d = ref({})
const trend = computed(() => d.value.trend || [])
const top = computed(() => (d.value.topPartners || []).slice(0, 10))
const maxV = computed(() => Math.max(1, ...trend.value.flatMap((t) => [Number(g(t, 'ar') || 0), Number(g(t, 'ap') || 0)])))
const barH = (v) => Math.round((Number(v || 0) / maxV.value) * 130)

const stats = computed(() => [
  { label: '本月应收(含税)', value: money(d.value.monthAr), color: '#67c23a' },
  { label: '本月应付(含税)', value: money(d.value.monthAp), color: '#e6a23c' },
  { label: '本月毛利', value: money(Number(d.value.monthAr || 0) - Number(d.value.monthAp || 0)), color: '#409eff' },
  { label: '应收未收(已确认)', value: money(d.value.arOutstanding), color: '#f56c6c' },
  { label: '应付未付(已确认)', value: money(d.value.apOutstanding), color: '#f56c6c' },
  { label: '逾期对账单', value: d.value.overdueStatements ?? 0, color: '#f56c6c' }
])
const todos = computed(() => [
  { label: '待计费单据', value: d.value.pendingDocs, to: '/billing/doc?billStatus=PENDING', color: '#409eff' },
  { label: '计费失败单据', value: d.value.failedDocs, to: '/billing/doc?billStatus=FAILED', color: '#f56c6c' },
  { label: '未对账应收', value: money(d.value.unstatementedAr), to: '/billing/fee?direction=AR&status=NEW', color: '#67c23a' },
  { label: '未对账应付', value: money(d.value.unstatementedAp), to: '/billing/fee?direction=AP&status=NEW', color: '#e6a23c' },
  { label: '草稿对账单', value: d.value.draftStatements, to: '/settlement/statement?status=DRAFT', color: '#909399' },
  { label: '争议对账单', value: d.value.disputedStatements, to: '/settlement/statement?status=DISPUTED', color: '#e6a23c' },
  { label: '30日内到期合同', value: d.value.expiringContracts, to: '/contract/list?status=ACTIVE', color: '#e6a23c' }
])

onMounted(async () => { d.value = await report.dashboard() })
</script>

<style scoped>
.todo { text-align: center; padding: 10px 0; border-radius: 6px; cursor: pointer; background: #f5f7fa; margin-bottom: 8px; }
.todo:hover { background: #ecf5ff; }
.todo-value { font-size: 22px; font-weight: 600; }
.todo-label { color: #909399; font-size: 13px; }
.bars { display: flex; align-items: flex-end; gap: 4px; height: 170px; padding: 0 8px; overflow-x: auto; }
.bar-col { flex: 1; min-width: 14px; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; }
.bar-pair { display: flex; gap: 1px; align-items: flex-end; width: 100%; justify-content: center; }
.bar { width: 40%; border-radius: 3px 3px 0 0; min-height: 2px; }
.bar.ar, .dot.ar { background: #67c23a; }
.bar.ap, .dot.ap { background: #e6a23c; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 2px; vertical-align: middle; margin-right: 4px; }
.bar-day { font-size: 10px; color: #909399; margin-top: 4px; white-space: nowrap; }
</style>
