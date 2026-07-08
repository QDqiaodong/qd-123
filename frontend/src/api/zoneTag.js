import request from '@/utils/request'

export function getZoneTagList() {
  return request({
    url: '/zone-tag/list',
    method: 'get'
  })
}

export function getZoneTagById(id) {
  return request({
    url: `/zone-tag/${id}`,
    method: 'get'
  })
}

export function addZoneTag(data) {
  return request({
    url: '/zone-tag',
    method: 'post',
    data
  })
}

export function updateZoneTag(data) {
  return request({
    url: '/zone-tag',
    method: 'put',
    data
  })
}

export function deleteZoneTag(id) {
  return request({
    url: `/zone-tag/${id}`,
    method: 'delete'
  })
}
