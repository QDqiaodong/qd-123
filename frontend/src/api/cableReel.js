import request from '@/utils/request'

// 线缆盘（整盘电源线）档案分页：可按盘号关键词、状态、绑定配件筛选
export function getCableReelPage(params) {
  return request({
    url: '/cable-reel/page',
    method: 'get',
    params
  })
}

// 按盘号建档：盘号、绑定配件、盘上剩余（整盘）米数；建档为“未开盘”，米数不进配件档案，
// 返回新盘ID。同一盘号不能建两次（后端唯一索引兜底）
export function createCableReel(data) {
  return request({
    url: '/cable-reel',
    method: 'post',
    data
  })
}

// 开盘确认：整盘米数一次性计入配件档案现存量，盘置为已开盘；同一配件同时只开一个盘
export function openCableReel(id) {
  return request({
    url: `/cable-reel/${id}/open`,
    method: 'put'
  })
}

// 扣米：仅已开盘盘可扣，盘上剩余与配件档案现存量同事务扣减
export function deductCableReel(id, meters) {
  return request({
    url: `/cable-reel/${id}/deduct`,
    method: 'put',
    data: { meters }
  })
}

// 删除线缆盘：仅未开盘盘可删除
export function deleteCableReel(id) {
  return request({
    url: `/cable-reel/${id}`,
    method: 'delete'
  })
}
