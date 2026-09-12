import request from '@/utils/request'

// 分区盘点单分页：可按状态（0待确认/1已确认）、分区筛选
export function getStockCheckPage(params) {
  return request({
    url: '/stock-check/page',
    method: 'get',
    params
  })
}

// 盘点单详情：账面快照、实盘登记值与差异实时装配
export function getStockCheckById(id) {
  return request({
    url: `/stock-check/${id}`,
    method: 'get'
  })
}

// 按分区开盘：zoneTagId 为 null 表示未分配分区；空分区也允许开盘，返回新单ID
export function createStockCheck(data) {
  return request({
    url: '/stock-check',
    method: 'post',
    data
  })
}

// 登记实盘数：仅待确认单可改，可部分登记、反复覆盖登记，不修改库存
export function recordStockCheckItems(id, items) {
  return request({
    url: `/stock-check/${id}/items`,
    method: 'put',
    data: { items }
  })
}

// 确认盘点：按实盘数一次性回写现存量并锁单，已删除配件不回写
export function confirmStockCheck(id, confirmRemark) {
  return request({
    url: `/stock-check/${id}/confirm`,
    method: 'put',
    data: { confirmRemark }
  })
}

// 删除盘点单：仅待确认单可删除
export function deleteStockCheck(id) {
  return request({
    url: `/stock-check/${id}`,
    method: 'delete'
  })
}

// 导出已确认盘点单的差异明细：返回完整 axios response（响应体为 Blob）；
// 待确认单后端拒绝并返回原因
export function exportStockCheckDiff(id) {
  return request({
    url: `/stock-check/${id}/diff-export`,
    method: 'get',
    responseType: 'blob',
    timeout: 60000
  })
}
