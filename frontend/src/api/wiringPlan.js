import request from '@/utils/request'

export function getWiringPlanPage(params) {
  return request({
    url: '/wiring-plan/page',
    method: 'get',
    params
  })
}

export function getWiringPlanById(id) {
  return request({
    url: `/wiring-plan/${id}`,
    method: 'get'
  })
}

export function addWiringPlan(data) {
  return request({
    url: '/wiring-plan',
    method: 'post',
    data
  })
}

export function updateWiringPlan(data) {
  return request({
    url: '/wiring-plan',
    method: 'put',
    data
  })
}

export function deleteWiringPlan(id) {
  return request({
    url: `/wiring-plan/${id}`,
    method: 'delete'
  })
}

export function updateWiringPlanStatus(id, status) {
  return request({
    url: `/wiring-plan/${id}/status`,
    method: 'put',
    params: { status }
  })
}

// 导出当前筛选结果：返回完整 axios response（响应体为 Blob）；全量导出可能耗时，放宽超时
export function exportWiringPlans(params) {
  return request({
    url: '/wiring-plan/export',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 60000
  })
}

// 库存缺口列表：需求合计只统计已启用且未核销方案，与配件档案、方案明细同一口径
export function getStockGaps() {
  return request({
    url: '/wiring-plan/stock-gaps',
    method: 'get'
  })
}

// 库存缺口按分区汇总：缺口件数与涉及配件种数，未分配分区单独一行，与缺口列表同一口径
export function getStockGapZoneSummary() {
  return request({
    url: '/wiring-plan/stock-gaps/zone-summary',
    method: 'get'
  })
}

// 导出缺口分区汇总 CSV：返回完整 axios response（响应体为 Blob）
export function exportStockGapZoneSummary() {
  return request({
    url: '/wiring-plan/stock-gaps/zone-summary/export',
    method: 'get',
    responseType: 'blob',
    timeout: 60000
  })
}

// 按方案核销出库：扣减配件现存量，同一方案不可重复核销
export function writeoffWiringPlan(id) {
  return request({
    url: `/wiring-plan/${id}/writeoff`,
    method: 'put'
  })
}
