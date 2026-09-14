import request from '@/utils/request'

// 光纤熔接接头登记分页：可按接头编号关键词、所属分区、是否过 OTDR、可投运、在档/作废筛选
export function getFiberSplicePage(params) {
  return request({
    url: '/fiber-splice/page',
    method: 'get',
    params
  })
}

// 登记新接头：接头编号、所属分区、盘留米数、是否过 OTDR；
// 登记时一律不可投运，需通过 OTDR 后再单独“标记可投运”。返回新接头ID
export function createFiberSplice(data) {
  return request({
    url: '/fiber-splice',
    method: 'post',
    data
  })
}

// 编辑在档接头：所属分区、盘留米数、是否过 OTDR、备注；接头编号不可改，已作废只读
export function updateFiberSplice(id, data) {
  return request({
    url: `/fiber-splice/${id}`,
    method: 'put',
    data
  })
}

// 标记/取消可投运：未过 OTDR 不能标记可投运（后端强制）
export function commissionFiberSplice(id, commissionable) {
  return request({
    url: `/fiber-splice/${id}/commission`,
    method: 'put',
    data: { commissionable }
  })
}

// 作废：只置“已作废”留档，不做物理删除；作废原因可填
export function voidFiberSplice(id, reason) {
  return request({
    url: `/fiber-splice/${id}/void`,
    method: 'put',
    data: { reason }
  })
}
