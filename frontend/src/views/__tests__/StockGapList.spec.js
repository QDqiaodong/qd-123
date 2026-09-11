import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import StockGapList from '@/views/StockGapList.vue'
import { getStockGaps, getWiringPlanPage } from '@/api/wiringPlan'

vi.mock('@/api/wiringPlan', () => ({
  getStockGaps: vi.fn(),
  getWiringPlanPage: vi.fn()
}))

const gapRows = () => [
  {
    accessoryId: 100,
    accessoryName: 'RVV电源线',
    model: 'RVV-2*1.0',
    specUnit: 'mm²',
    zoneTagId: 2,
    zoneTagName: '线缆布线区',
    stockQuantity: 500,
    requiredQuantity: 600,
    gapQuantity: 100,
    shortage: true,
    unassignedZone: false,
    accessoryDeleted: false
  },
  {
    accessoryId: 101,
    accessoryName: '防爆摄像头',
    model: 'DS-2CD3T46',
    specUnit: 'MP',
    zoneTagId: 5,
    zoneTagName: '监控设备区',
    stockQuantity: 20,
    requiredQuantity: 0,
    gapQuantity: 0,
    shortage: false,
    unassignedZone: false,
    accessoryDeleted: false
  },
  {
    accessoryId: 102,
    accessoryName: '扎带',
    model: 'ZD-4*200',
    specUnit: 'mm',
    zoneTagId: null,
    zoneTagName: null,
    stockQuantity: 400,
    requiredQuantity: 300,
    gapQuantity: 0,
    shortage: false,
    unassignedZone: true,
    accessoryDeleted: false
  },
  {
    accessoryId: 999,
    accessoryName: '旧型号配件',
    model: 'OLD-1',
    specUnit: null,
    zoneTagId: null,
    zoneTagName: null,
    stockQuantity: 0,
    requiredQuantity: 30,
    gapQuantity: 0,
    shortage: false,
    unassignedZone: true,
    accessoryDeleted: true
  }
]

const mountPage = async () => {
  getStockGaps.mockResolvedValue(gapRows())
  getWiringPlanPage.mockResolvedValue({
    records: [{ id: 1, writeoff: true }, { id: 2, writeoff: false }],
    total: 2
  })
  const wrapper = mount(StockGapList, {
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

describe('库存缺口分析', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载时加载缺口列表', async () => {
    await mountPage()
    expect(getStockGaps).toHaveBeenCalledTimes(1)
  })

  it('现存量不足的配件标红并计入统计', async () => {
    const wrapper = await mountPage()

    const shortageRow = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('RVV电源线'))
    expect(shortageRow.classes()).toContain('shortage-row')
    expect(shortageRow.text()).toContain('600')
    expect(shortageRow.text()).toContain('100')
    const redTexts = shortageRow.findAll('.shortage-text')
    expect(redTexts.length).toBeGreaterThan(0)

    // 汇总标签：3 种不足? 不，只有 1 种不足；未分配 2 种；已删除 1 种
    const summary = wrapper.find('.summary-tags').text()
    expect(summary).toContain('现存不足 1 种')
    expect(summary).toContain('未分配分区 2 种')
    expect(summary).toContain('已核销方案 1 个')
    expect(summary).toContain('已删除配件 1 种')

    wrapper.unmount()
  })

  it('未分配分区单独列出（含已删除配件）', async () => {
    const wrapper = await mountPage()

    const unassignedSection = wrapper.find('.unassigned-section')
    expect(unassignedSection.exists()).toBe(true)
    const unassignedNames = unassignedSection
      .findAll('tbody tr td:first-child')
      .map((el) => el.text().trim())
    expect(unassignedNames.some((n) => n.includes('扎带'))).toBe(true)
    expect(unassignedNames.some((n) => n.includes('旧型号配件'))).toBe(true)
    expect(unassignedSection.text()).toContain('已删除')

    wrapper.unmount()
  })

  it('已删除配件不标红、缺口显示为 -', async () => {
    const wrapper = await mountPage()

    const deletedRow = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('旧型号配件'))
    expect(deletedRow.classes()).toContain('deleted-row')
    expect(deletedRow.classes()).not.toContain('shortage-row')
    expect(deletedRow.text()).toContain('不参与合计')

    wrapper.unmount()
  })

  it('点击刷新重新拉取数据', async () => {
    const wrapper = await mountPage()

    const refreshBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('刷新'))
    await refreshBtn.trigger('click')
    await flushPromises()

    expect(getStockGaps).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('全部已分配分区时展示空状态', async () => {
    getStockGaps.mockResolvedValue([
      {
        accessoryId: 100,
        accessoryName: 'RVV电源线',
        model: 'RVV',
        specUnit: 'mm²',
        zoneTagId: 2,
        zoneTagName: '线缆布线区',
        stockQuantity: 1000,
        requiredQuantity: 600,
        gapQuantity: 0,
        shortage: false,
        unassignedZone: false,
        accessoryDeleted: false
      }
    ])
    getWiringPlanPage.mockResolvedValue({ records: [], total: 0 })
    const wrapper = mount(StockGapList, {
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    expect(wrapper.find('.unassigned-section .el-empty').exists()).toBe(true)
    expect(wrapper.find('.summary-tags').text()).toContain('未分配分区 0 种')

    wrapper.unmount()
  })
})
