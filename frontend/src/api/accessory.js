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
// 未设下限/已删除/现存量不低于下限的不进台账；数据实时计算
export function getSafetyStockShortages() {
  return request({
    url: '/accessory/safety-stock',
    method: 'get'
  })
}
