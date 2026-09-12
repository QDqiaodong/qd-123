import request from '@/utils/request'

export function getAccessoryPage(params) {
  return request({
    url: '/accessory/page',
    method: 'get',
    params
  })
}

export function getAccessoryById(id) {
  return request({
    url: `/accessory/${id}`,
    method: 'get'
  })
}

export function addAccessory(data) {
  return request({
    url: '/accessory',
    method: 'post',
    data
  })
}

export function updateAccessory(data) {
  return request({
    url: '/accessory',
    method: 'put',
    data
  })
}

export function deleteAccessory(id) {
  return request({
    url: `/accessory/${id}`,
    method: 'delete'
  })
}

export function updateAccessoryZone(id, zoneTagId) {
  return request({
    url: `/accessory/${id}/zone`,
    method: 'put',
    params: { zoneTagId }
  })
}

// 安全库存台账：仅返回设了安全库存下限且现存量低于下限的配件（含未分配分区），
// 未设下限/已删除/现存量不低于下限的不进台账；数据实时计算。
// 按库房分区领料筛选：zoneTagId 只看指定分区；unassignedZone=true 只看未分配分区；
// 都不传返回全集。换分区或改下限后重新拉取，行数与缺口跟页面一致
export function getSafetyStockShortages(params) {
  return request({
    url: '/accessory/safety-stock',
    method: 'get',
    params
  })
}

// 导出当前分区筛选下的台账 CSV（名称、分区、现存、下限、缺口）：
// 返回完整 axios response（响应体为 Blob）；与页面台账同源同筛选参数
export function exportSafetyStockShortages(params) {
  return request({
    url: '/accessory/safety-stock/export',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 60000
  })
}
