import request from '@/utils/request'

// 补货单分页：可按状态（0待提交/1已提交/2已作废）筛选
export function getReplenishOrderPage(params) {
  return request({
    url: '/replenish-order/page',
    method: 'get',
    params
  })
}

// 补货单详情：明细 + 按分区汇总缺口件数，实时装配
export function getReplenishOrderById(id) {
  return request({
    url: `/replenish-order/${id}`,
    method: 'get'
  })
}

// 勾选台账低位配件生成补货单（待提交草稿），返回新单ID
// items: [{ accessoryId, replenishQuantity? }]，replenishQuantity 不传时后端按实时缺口补齐
export function createReplenishOrder(items) {
  return request({
    url: '/replenish-order',
    method: 'post',
    data: { items }
  })
}

// 调整补货数量：仅待提交草稿可改，可部分行、反复覆盖
// items: [{ itemId, replenishQuantity }]
export function updateReplenishOrderItems(id, items) {
  return request({
    url: `/replenish-order/${id}/items`,
    method: 'put',
    data: { items }
  })
}

// 提交补货单：回写配件档案补货单号与待补数量，提交后数量锁定
export function submitReplenishOrder(id) {
  return request({
    url: `/replenish-order/${id}/submit`,
    method: 'put'
  })
}

// 作废已提交补货单：清除档案待补标记，单据只读留档
export function cancelReplenishOrder(id, cancelReason) {
  return request({
    url: `/replenish-order/${id}/cancel`,
    method: 'put',
    data: { cancelReason }
  })
}

// 删除补货单：仅待提交草稿可删除
export function deleteReplenishOrder(id) {
  return request({
    url: `/replenish-order/${id}`,
    method: 'delete'
  })
}
