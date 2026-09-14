import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import SafetyStockList from '@/views/SafetyStockList.vue'
import { getSafetyStockShortages } from '@/api/accessory'
import { createReplenishOrder } from '@/api/replenishOrder'
import { __resetStockListenersForTests } from '@/utils/stockSync'

const push = vi.fn()

vi.mock('@/api/accessory', () => ({
  getSafetyStockShortages: vi.fn(),
  exportSafetyStockShortages: vi.fn()
}))

vi.mock('@/api/replenishOrder', () => ({
  createReplenishOrder: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

// 3 个低位件：桥架区 1 个缺口 20、线缆区 1 个缺口 200、未分配 1 个缺口 5
const ledgerRows = () => [
  {
    accessoryId: 1,
    accessoryName: '镀锌桥架',
    model: 'XQJ-C-200',
    specUnit: 'mm',
    zoneTagId: 1,
    zoneTagName: '弱电桥架区',
    stockQuantity: 80,
    safetyStock: 100,
    gapQuantity: 20,
    urgent: false,
    unassignedZone: false,
    replenishPending: false
  },
  {
    accessoryId: 4,
    accessoryName: '六类网线',
    model: 'CAT6-UTP',
    specUnit: 'mm',
    zoneTagId: 2,
    zoneTagName: '线缆布线区',
    stockQuantity: 600,
    safetyStock: 800,
    gapQuantity: 200,
    urgent: false,
    unassignedZone: false,
    replenishPending: false
  },
  {
    accessoryId: 40,
    accessoryName: '未分配低位件',
    model: 'NA-1',
    specUnit: null,
    zoneTagId: null,
    zoneTagName: null,
    stockQuantity: 5,
    safetyStock: 10,
    gapQuantity: 5,
    urgent: true,
    unassignedZone: true,
    replenishPending: false
  }
]

const mountPage = async () => {
  getSafetyStockShortages.mockResolvedValue(ledgerRows())
  const wrapper = mount(SafetyStockList, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

const findButton = (wrapper, text) =>
  wrapper.findAll('button').find((b) => b.text().includes(text))

describe('安全库存台账 - 勾选生成补货单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  it('未勾选时生成按钮禁用；勾选后统计种数、缺口合计与分区小计', async () => {
    const wrapper = await mountPage()

    expect(findButton(wrapper, '生成补货单').attributes('disabled')).toBeDefined()

    // 勾选已分配表两行（桥架、线缆）；只取 tbody 内行首勾选格，避开表头全选
    const assignedCheckboxes = wrapper.findAll('.assigned-table tbody tr .el-checkbox')
    await assignedCheckboxes[0].trigger('click')
    await assignedCheckboxes[1].trigger('click')
    await flushPromises()

    const bar = wrapper.find('.replenish-bar').text()
    expect(bar).toContain('2')
    expect(bar).toContain('220')
    expect(bar).toContain('弱电桥架区 20 件')
    expect(bar).toContain('线缆布线区 200 件')

    wrapper.unmount()
  })

  it('打开生成框可调整补货数量，分区汇总随调整重算并提交创建', async () => {
    createReplenishOrder.mockResolvedValue(88)
    const wrapper = await mountPage()

    // 只勾选六类网线（第二行）
    const assignedCheckboxes = wrapper.findAll('.assigned-table tbody tr .el-checkbox')
    await assignedCheckboxes[1].trigger('click')
    await flushPromises()

    await findButton(wrapper, '生成补货单').trigger('click')
    await flushPromises()

    // 弹窗默认按缺口 200，分区汇总显示 1 种 / 200 件
    const dialog = document.querySelector('.el-dialog')
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toContain('线缆布线区：1 种 / 200 件')

    // 确认创建：携带 accessoryId 与补货数量
    const confirmBtn = Array.from(dialog.querySelectorAll('button'))
      .find((b) => b.textContent.includes('生成补货单'))
    await confirmBtn.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(createReplenishOrder).toHaveBeenCalledTimes(1)
    const payload = createReplenishOrder.mock.calls[0][0]
    expect(payload).toEqual([{ accessoryId: 4, replenishQuantity: 200 }])
    // 创建成功后跳转到补货单页并带上新单 id 自动打开
    expect(push).toHaveBeenCalledWith({ path: '/replenish-order', query: { open: 88 } })

    wrapper.unmount()
  })

  it('已在补货中的配件显示单号且复选框禁用，不能重复勾选', async () => {
    const rows = ledgerRows().map((row) =>
      row.accessoryId === 4
        ? { ...row, replenishPending: true, replenishOrderNo: 'BH20260912100000001' }
        : row
    )
    getSafetyStockShortages.mockResolvedValue(rows)
    const wrapper = mount(SafetyStockList, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    const text = wrapper.find('.assigned-table').text()
    expect(text).toContain('补货中 BH20260912100000001')

    // element-plus 对 selectable=false 的行，勾选格渲染为 disabled checkbox
    const checkboxes = wrapper.findAll('.assigned-table tbody .el-checkbox')
    // 第一列桥架可选；第二列六类网线（已占用）禁用
    expect(checkboxes[0].classes()).not.toContain('is-checked')
    const secondRow = wrapper.findAll('.assigned-table tbody tr')[1]
    expect(secondRow.find('.el-checkbox').classes()).toContain('is-disabled')

    wrapper.unmount()
  })
})
