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
