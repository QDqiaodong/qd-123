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
