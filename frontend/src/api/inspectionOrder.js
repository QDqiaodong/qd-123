import request from '@/utils/request'

// 辅材送检单分页：可按是否已回样、是否合格、送检单号/配件名称关键词筛选
export function getInspectionPage(params) {
  return request({
    url: '/inspection-order/page',
    method: 'get',
    params
  })
}

// 新建送检单：按已建档配件送检，必须写明送检批次与实验室名称；
// 批次或实验室未填完整不能提交。返回新送检单ID，新建后单据待回样
export function createInspection(data) {
  return request({
    url: '/inspection-order',
    method: 'post',
    data
  })
}

// 实验室写回结论：仅待回样单可写回，结论非空白；写回后单据变为已回样
export function writeInspectionResult(id, labConclusion) {
  return request({
    url: `/inspection-order/${id}/result`,
    method: 'put',
    data: { labConclusion }
  })
}

// 标记/取消合格：未回样不能标合格（后端强制）
export function qualifyInspection(id, qualified) {
  return request({
    url: `/inspection-order/${id}/qualify`,
    method: 'put',
    data: { qualified }
  })
}
