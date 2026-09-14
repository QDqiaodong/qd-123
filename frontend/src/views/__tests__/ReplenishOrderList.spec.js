import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import ReplenishOrderList from '@/views/ReplenishOrderList.vue'
import {
  getReplenishOrderPage,
  getReplenishOrderById,
  updateReplenishOrderItems,
  submitReplenishOrder,
  cancelReplenishOrder,
  deleteReplenishOrder
} from '@/api/replenishOrder'
import { notifyStockChanged, __resetStockListenersForTests } from '@/utils/stockSync'

const replace = vi.fn()

vi.mock('@/api/replenishOrder', () => ({
  getReplenishOrderPage: vi.fn(),
  getReplenishOrderById: vi.fn(),
  updateReplenishOrderItems: vi.fn(),
  submitReplenishOrder: vi.fn(),
  cancelReplenishOrder: vi.fn(),
  deleteReplenishOrder: vi.fn()
}))

vi.mock('@/utils/stockSync', () => ({
  notifyStockChanged: vi.fn(),
  onStockChanged: vi.fn(() => () => {}),
  __resetStockListenersForTests: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace }),
  useRoute: () => ({ query: {}, path: '/replenish-order' })
}))

const draftHeader = () => ({
  id: 10,
  replenishNo: 'BH20260912100000001',
  status: 0,
  statusText: '待提交',
  itemCount: 2,
  totalQuantity: 220,
  zoneCount: 2,
  cancelReason: null,
  submitTime: null,
  cancelTime: null,
  createTime: '2026-09-12 10:00:00'
})

const draftDetail = () => ({
  header: draftHeader(),
  conflictCount: 0,
  zoneSummaries: [
    { zoneTagId: 2, zoneName: '线缆布线区', unassignedZone: false, accessoryCount: 1, totalQuantity: 200 },
    { zoneTagId: null, zoneName: '未分配分区', unassignedZone: true, accessoryCount: 1, totalQuantity: 20 }
  ],
  items: [
    {
      id: 1001,
      accessoryId: 4,
      accessoryName: '六类网线',
      model: 'CAT6',
      zoneTagId: 2,
      zoneName: '线缆布线区',
      unassignedZone: false,
      stockQuantity: 600,
      safetyStock: 800,
      gapQuantity: 200,
      replenishQuantity: 200,
      accessoryDeleted: false,
      pendingConflict: false
    },
    {
      id: 1002,
      accessoryId: 40,
      accessoryName: '未分配低位件',
      model: 'NA-1',
      zoneTagId: null,
      zoneName: '未分配分区',
      unassignedZone: true,
      stockQuantity: 5,
      safetyStock: 10,
      gapQuantity: 20,
      replenishQuantity: 20,
      accessoryDeleted: false,
      pendingConflict: false
    }
  ]
})

const mountPage = async () => {
  getReplenishOrderPage.mockResolvedValue({ records: [draftHeader()], total: 1 })
  getReplenishOrderById.mockResolvedValue(draftDetail())
  const wrapper = mount(ReplenishOrderList, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

const openDrawer = async (wrapper) => {
  const viewBtn = wrapper
    .findAll('.el-table__row')[0]
    .findAll('button')
    .find((b) => b.text().includes('编辑/查看'))
  await viewBtn.trigger('click')
  await flushPromises()
}

const drawer = () => document.querySelector('.el-drawer')

describe('补货单列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  it('列表展示单号、状态、种数、分区数与补货合计', async () => {
    const wrapper = await mountPage()
    const text = wrapper.find('.el-table__row').text()
    expect(text).toContain('BH20260912100000001')
    expect(text).toContain('待提交')
    expect(text).toContain('2 种')
    expect(text).toContain('2 个')
    expect(text).toContain('220 件')
    wrapper.unmount()
  })

  it('详情抽屉按分区汇总缺口件数，未分配分区殿后', async () => {
    const wrapper = await mountPage()
    await openDrawer(wrapper)

    const d = drawer()
    const zoneRows = d.querySelectorAll('.zone-summary-table .el-table__row')
    expect(zoneRows.length).toBe(2)
    expect(zoneRows[0].textContent).toContain('线缆布线区')
    expect(zoneRows[0].textContent).toContain('200')
    expect(zoneRows[1].textContent).toContain('未分配分区')
    expect(zoneRows[1].textContent).toContain('20')
    expect(d.textContent).toContain('合计：2 种 / 220 件')

    wrapper.unmount()
  })

  it('草稿调整补货数量需先保存，提交确认后回写档案并广播刷新', async () => {
    submitReplenishOrder.mockResolvedValue({})
    const wrapper = await mountPage()
    await openDrawer(wrapper)

    // 直接提交（无脏数据）走二次确认框
    const d = drawer()
    const submitBtn = Array.from(d.querySelectorAll('button'))
      .find((b) => b.textContent.includes('提交补货单'))
    await submitBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    // element-plus message-box 确认
    const confirm = Array.from(document.querySelectorAll('.el-message-box button'))
      .find((b) => b.textContent.includes('确认提交'))
    await confirm.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(submitReplenishOrder).toHaveBeenCalledWith(10)
    // 提交后通知配件档案/台账刷新待补标记
    expect(notifyStockChanged).toHaveBeenCalledWith('replenish-order')

    wrapper.unmount()
  })

  it('已提交单数量只读、只提供作废入口；作废清除待补标记', async () => {
    const submitted = draftDetail()
    submitted.header = { ...draftHeader(), status: 1, statusText: '已提交', submitTime: '2026-09-12 11:00:00' }
    getReplenishOrderPage.mockResolvedValue({
      records: [{ ...draftHeader(), status: 1, statusText: '已提交' }],
      total: 1
    })
    getReplenishOrderById.mockResolvedValue(submitted)
    cancelReplenishOrder.mockResolvedValue({})

    const wrapper = mount(ReplenishOrderList, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()
    const viewBtn = wrapper
      .findAll('.el-table__row')[0]
      .findAll('button')
      .find((b) => b.text().includes('查看'))
    await viewBtn.trigger('click')
    await flushPromises()

    const d = drawer()
    // 已提交单不渲染数量输入框，只展示数字；没有“保存数量/提交”按钮
    expect(d.querySelector('.el-input-number')).toBeNull()
    expect(d.textContent).toContain('数量不可再修改')
    const cancelEntry = Array.from(d.querySelectorAll('button'))
      .find((b) => b.textContent.includes('作废补货单'))
    expect(cancelEntry).toBeTruthy()

    // 点作废 → 弹作废原因框 → 确认作废
    await cancelEntry.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    const dialog = document.querySelector('.el-dialog')
    const textarea = dialog.querySelector('textarea')
    textarea.value = '采购计划调整'
    await textarea.dispatchEvent(new Event('input', { bubbles: true }))
    const confirmCancel = Array.from(dialog.querySelectorAll('button'))
      .find((b) => b.textContent.includes('确认作废'))
    await confirmCancel.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(cancelReplenishOrder).toHaveBeenCalledWith(10, '采购计划调整')
    expect(notifyStockChanged).toHaveBeenCalledWith('replenish-order')

    wrapper.unmount()
  })
})
