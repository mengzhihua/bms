import http from './request'

export const crud = (base) => ({
  page: (params) => http.get(`${base}/page`, { params }),
  list: (params) => http.get(`${base}/list`, { params }),
  get: (id) => http.get(`${base}/${id}`),
  create: (data) => http.post(base, data),
  update: (id, data) => http.put(`${base}/${id}`, data),
  remove: (id) => http.delete(`${base}/${id}`)
})

export const basic = {
  partner: crud('/basic/partner'),
  warehouse: crud('/basic/warehouse'),
  chargeItem: crud('/basic/charge-item')
}

export const contract = {
  ...crud('/contract'),
  activate: (id) => http.post(`/contract/${id}/activate`),
  terminate: (id) => http.post(`/contract/${id}/terminate`),
  reopen: (id) => http.post(`/contract/${id}/reopen`),
  expire: () => http.post('/contract/expire'),
  rules: (id) => http.get(`/contract/${id}/rules`),
  saveRule: (id, data) => http.post(`/contract/${id}/rules`, data),
  removeRule: (id, ruleId) => http.delete(`/contract/${id}/rules/${ruleId}`)
}

export const doc = {
  page: (params) => http.get('/billing/doc/page', { params }),
  statusCount: () => http.get('/billing/doc/status-count'),
  get: (docNo) => http.get(`/billing/doc/${docNo}`),
  create: (data, autoBill = true) => http.post('/billing/doc', data, { params: { autoBill } }),
  bill: (docNo) => http.post(`/billing/doc/${docNo}/bill`),
  billPending: () => http.post('/billing/doc/bill-pending'),
  ignore: (docNo, reason) => http.post(`/billing/doc/${docNo}/ignore`, { reason }),
  reopen: (docNo) => http.post(`/billing/doc/${docNo}/reopen`),
  importCsv: (file, autoBill = true) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/billing/doc/import', fd, { params: { autoBill }, headers: { 'Content-Type': 'multipart/form-data' } })
  }
}

export const fee = {
  page: (params) => http.get('/billing/fee/page', { params }),
  summary: (params) => http.get('/billing/fee/summary', { params }),
  get: (feeNo) => http.get(`/billing/fee/${feeNo}`),
  manual: (data) => http.post('/billing/fee/manual', data),
  cancel: (feeNo, reason) => http.post(`/billing/fee/${feeNo}/cancel`, { reason })
}

export const statement = {
  page: (params) => http.get('/settlement/statement/page', { params }),
  get: (no) => http.get(`/settlement/statement/${no}`),
  generate: (data) => http.post('/settlement/statement/generate', data),
  generateBatch: (params) => http.post('/settlement/statement/generate-batch', null, { params }),
  confirm: (no) => http.post(`/settlement/statement/${no}/confirm`),
  dispute: (no, reason) => http.post(`/settlement/statement/${no}/dispute`, { reason }),
  cancel: (no, reason) => http.post(`/settlement/statement/${no}/cancel`, { reason }),
  adjust: (no, data) => http.post(`/settlement/statement/${no}/adjust`, data),
  removeFee: (no, feeNo) => http.delete(`/settlement/statement/${no}/fees/${feeNo}`)
}

export const invoice = {
  page: (params) => http.get('/settlement/invoice/page', { params }),
  issue: (data) => http.post('/settlement/invoice', data),
  cancel: (no, reason) => http.post(`/settlement/invoice/${no}/cancel`, { reason })
}

export const payment = {
  page: (params) => http.get('/settlement/payment/page', { params }),
  get: (no) => http.get(`/settlement/payment/${no}`),
  create: (data) => http.post('/settlement/payment', data),
  remove: (no) => http.delete(`/settlement/payment/${no}`),
  apply: (data) => http.post('/settlement/payment/apply', data),
  unapply: (applyId) => http.delete(`/settlement/payment/apply/${applyId}`)
}

export const integration = {
  logPage: (params) => http.get('/integration/log/page', { params })
}

export const report = {
  dashboard: () => http.get('/report/dashboard'),
  feeSummary: (params) => http.get('/report/fee-summary', { params }),
  feeByItem: (params) => http.get('/report/fee-by-item', { params }),
  profit: (params) => http.get('/report/profit', { params }),
  aging: (direction) => http.get('/report/aging', { params: { direction } }),
  ledger: (params) => http.get('/report/partner-ledger', { params })
}

export const system = {
  user: crud('/system/user'),
  oplogPage: (params) => http.get('/system/oplog/page', { params })
}

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
  changePassword: (data) => http.post('/auth/password', data)
}

/** 带 token 下载后端 CSV（使用 blob，避开 URL 中传 token） */
export async function downloadCsv(url, params, filename) {
  const blob = await http.get(url, { params, responseType: 'blob' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}
