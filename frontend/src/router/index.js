import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { auth, isAdmin } from '../auth'

export const menus = [
  { path: '/dashboard', name: '工作台', icon: 'Odometer', component: () => import('../views/Dashboard.vue') },
  {
    path: '/contract', name: '合同与费率', icon: 'Document',
    children: [
      { path: 'list', name: '结算合同', component: () => import('../views/contract/ContractList.vue') }
    ]
  },
  {
    path: '/billing', name: '计费中心', icon: 'Coin',
    children: [
      { path: 'doc', name: '业务单据', component: () => import('../views/billing/DocList.vue') },
      { path: 'import', name: '单据导入', component: () => import('../views/billing/DocImport.vue') },
      { path: 'fee', name: '费用明细', component: () => import('../views/billing/FeeList.vue') }
    ]
  },
  {
    path: '/settlement', name: '对账结算', icon: 'Tickets',
    children: [
      { path: 'statement', name: '对账单', component: () => import('../views/settlement/StatementList.vue') },
      { path: 'invoice', name: '发票管理', component: () => import('../views/settlement/InvoiceList.vue') },
      { path: 'payment', name: '收付款核销', component: () => import('../views/settlement/PaymentList.vue') }
    ]
  },
  {
    path: '/basic', name: '基础数据', icon: 'Setting',
    children: [
      { path: 'partner', name: '结算对象', component: () => import('../views/basic/Partner.vue') },
      { path: 'warehouse', name: '仓库', component: () => import('../views/basic/Warehouse.vue') },
      { path: 'charge-item', name: '费用项目', component: () => import('../views/basic/ChargeItem.vue') }
    ]
  },
  {
    path: '/integration', name: '系统集成', icon: 'Connection',
    children: [
      { path: 'log', name: '接口日志', component: () => import('../views/integration/IntegrationLog.vue') },
      { path: 'openapi', name: '开放接口说明', component: () => import('../views/integration/OpenApiDoc.vue') }
    ]
  },
  { path: '/report', name: '报表分析', icon: 'DataAnalysis', component: () => import('../views/report/Report.vue') },
  {
    path: '/system', name: '系统管理', icon: 'Tools', adminOnly: true,
    children: [
      { path: 'user', name: '用户管理', component: () => import('../views/system/User.vue') },
      { path: 'oplog', name: '操作日志', component: () => import('../views/system/OpLog.vue') }
    ]
  }
]

/** 当前用户可见菜单（adminOnly 菜单仅管理员可见；后端同样做了鉴权） */
export const visibleMenus = () => menus.filter((m) => !m.adminOnly || isAdmin())

const routes = [
  { path: '/login', name: '登录', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      ...menus.flatMap((m) =>
        m.children
          ? m.children.map((c) => ({ path: `${m.path}/${c.path}`, name: c.name, component: c.component }))
          : [{ path: m.path, name: m.name, component: m.component }]
      ),
      { path: '/contract/detail/:id', name: '合同详情', component: () => import('../views/contract/ContractDetail.vue') },
      { path: '/billing/doc/:docNo', name: '单据详情', component: () => import('../views/billing/DocDetail.vue') },
      { path: '/settlement/statement/:statementNo', name: '对账单详情', component: () => import('../views/settlement/StatementDetail.vue') }
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return auth.token ? '/dashboard' : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/system') && !isAdmin()) return '/dashboard'
  return true
})

export default router
