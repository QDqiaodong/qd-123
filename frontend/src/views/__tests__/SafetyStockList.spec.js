import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import SafetyStockList from '@/views/SafetyStockList.vue'
import { getSafetyStockShortages } from '@/api/accessory'
import { notifyStockChanged, __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/accessory', () => ({
  getSafetyStockShortages: vi.fn()
}))

// 台账口径：只含设了下限且现存低于下限的正常配件；未设下限/不低于下限的不应出现在 mock 数据里
const rows = () => [
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
    unassignedZone: false
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
    unassignedZone: false
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
    unassignedZone: true
  }
]

const mountPage = async () => {
  getSafetyStockShortages.mockResolvedValue(rows())
  const wrapper = mount(SafetyStockList, {
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

describe('安全库存台账', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
  })

  it('挂载时加载台账，列出名称/分区/现存量/下限/缺口', async () => {
    const wrapper = await mountPage()
    expect(getSafetyStockShortages).toHaveBeenCalledTimes(1)

    const text = wrapper.text()
    expect(text).toContain('六类网线')
    expect(text).toContain('线缆布线区')
    expect(text).toContain('600')
    expect(text).toContain('800')
    expect(text).toContain('200')
  })

  it('统计条数与缺口合计，未分配低位单独成组', async () => {
    const wrapper = await mountPage()

    const summary = wrapper.find('.summary-tags').text()
    expect(summary).toContain('待补货 3 种')
    expect(summary).toContain('其中未分配分区 1 种')
    // 20 + 200 + 5 = 225
    expect(summary).toContain('缺口合计 225 件')

    // 已分配表只有 2 行
    const assignedRows = wrapper.findAll('.assigned-table tbody tr')
    expect(assignedRows.length).toBe(2)

    // 未分配分区单独列出，低位件不漏
    const section = wrapper.find('.unassigned-section')
    expect(section.exists()).toBe(true)
    expect(section.text()).toContain('未分配低位件')
    expect(section.text()).toContain('未分配')
    const unassignedNames = section
      .findAll('tbody tr td:first-child')
      .map((el) => el.text().trim())
    expect(unassignedNames).toContain('未分配低位件')

    wrapper.unmount()
  })

  it('点击刷新重新拉取', async () => {
    const wrapper = await mountPage()

    const refreshBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('刷新'))
    await refreshBtn.trigger('click')
    await flushPromises()

    expect(getSafetyStockShortages).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('改下限或库存后随库存变更通知实时重算，条数与缺口与新数据一致', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('.summary-tags').text()).toContain('待补货 3 种')

    // 六类网线补到下限以上并把镀锌桥下限清空（不再进台账），只剩未分配低位件
    getSafetyStockShortages.mockResolvedValueOnce([
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
        unassignedZone: true
      }
    ])
    notifyStockChanged('accessory')
    await flushPromises()

    expect(getSafetyStockShortages).toHaveBeenCalledTimes(2)
    const summary = wrapper.find('.summary-tags').text()
    expect(summary).toContain('待补货 1 种')
    expect(summary).toContain('缺口合计 5 件')
    expect(wrapper.text()).not.toContain('六类网线')
    // 未分配的低位仍在
    expect(wrapper.find('.unassigned-section').text()).toContain('未分配低位件')

    wrapper.unmount()
  })

  it('页面卸载后不再响应库存变更通知', async () => {
    const wrapper = await mountPage()
    wrapper.unmount()

    notifyStockChanged('accessory')
    await flushPromises()

    expect(getSafetyStockShortages).toHaveBeenCalledTimes(1)
  })

  it('台账为空时已分配表与未分配分组均显示空状态', async () => {
    getSafetyStockShortages.mockResolvedValue([])
    const wrapper = mount(SafetyStockList, {
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    expect(wrapper.find('.summary-tags').text()).toContain('待补货 0 种')
    expect(wrapper.find('.summary-tags').text()).toContain('缺口合计 0 件')
    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('已分配分区暂无低于下限的配件')

    wrapper.unmount()
  })
})
