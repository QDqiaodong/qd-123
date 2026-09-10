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
