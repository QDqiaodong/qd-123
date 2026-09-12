import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import StockGapList from '@/views/StockGapList.vue'
import {
  getStockGaps,
  getStockGapZoneSummary,
  exportStockGapZoneSummary,
  getWiringPlanPage
} from '@/api/wiringPlan'
import { notifyStockChanged, __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/wiringPlan', () => ({
  getStockGaps: vi.fn(),
  getStockGapZoneSummary: vi.fn(),
  exportStockGapZoneSummary: vi.fn(),
  getWiringPlanPage: vi.fn()
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn()
    }
  }
})

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

// 与 gapRows 同源的分区汇总：线缆布线区缺口 1 种 100 件，监控设备区与未分配分区无缺口
const zoneSummaryRows = () => [
  {
    zoneTagId: 2,
    zoneTagName: '线缆布线区',
    unassignedZone: false,
    shortageAccessoryCount: 1,
    gapQuantityTotal: 100
  },
  {
    zoneTagId: 5,
    zoneTagName: '监控设备区',
    unassignedZone: false,
    shortageAccessoryCount: 0,
    gapQuantityTotal: 0
  },
  {
    zoneTagId: null,
    zoneTagName: '未分配分区',
    unassignedZone: true,
    shortageAccessoryCount: 0,
    gapQuantityTotal: 0
  }
]

const mountPage = async () => {
  getStockGaps.mockResolvedValue(gapRows())
  getStockGapZoneSummary.mockResolvedValue(zoneSummaryRows())
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
    __resetStockListenersForTests()
    getStockGapZoneSummary.mockResolvedValue([])
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

  it('配件档案保存现存量后立即按新现存量重算缺口数字', async () => {
    const wrapper = await mountPage()
    // 初次加载：RVV电源线现存 500、需求 600、缺口 100
    const firstRow = () =>
      wrapper.findAll('.el-table__row').find((r) => r.text().includes('RVV电源线'))
    expect(firstRow().text()).toContain('100')

    // 配件档案把现存量补到 700：缺口变为 0，不再标红
    getStockGaps.mockResolvedValueOnce([
      {
        accessoryId: 100,
        accessoryName: 'RVV电源线',
        model: 'RVV-2*1.0',
        specUnit: 'mm²',
        zoneTagId: 2,
        zoneTagName: '线缆布线区',
        stockQuantity: 700,
        requiredQuantity: 600,
        gapQuantity: 0,
        shortage: false,
        unassignedZone: false,
        accessoryDeleted: false
      }
    ])
    notifyStockChanged('accessory')
    await flushPromises()

    expect(getStockGaps).toHaveBeenCalledTimes(2)
    const row = firstRow()
    expect(row.text()).toContain('700')
    expect(row.text()).toContain('600')
    expect(row.text()).toContain('0')
    expect(row.classes()).not.toContain('shortage-row')

    wrapper.unmount()
  })

  it('页面卸载后不再响应库存变更通知', async () => {
    const wrapper = await mountPage()
    wrapper.unmount()

    notifyStockChanged('accessory')
    await flushPromises()

    expect(getStockGaps).toHaveBeenCalledTimes(1)
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

  it('按分区汇总展示小计与合计，未分配分区单独一行', async () => {
    const wrapper = await mountPage()

    const card = wrapper.find('.zone-summary-card')
    expect(card.exists()).toBe(true)
    const bodyRows = card.findAll('.el-table__body-wrapper tbody tr')
    // 3 个分区行：线缆布线区、监控设备区、未分配分区（单独一行）
    expect(bodyRows.length).toBe(3)
    expect(bodyRows[0].text()).toContain('线缆布线区')
    expect(bodyRows[0].text()).toContain('100')
    expect(bodyRows[1].text()).toContain('监控设备区')
    expect(bodyRows[2].text()).toContain('未分配分区')
    expect(bodyRows[2].classes()).toContain('unassigned-zone-row')

    // 合计行：涉及配件种数 1、缺口件数 100，与分区小计之和一致
    const summaryRow = card.findAll('tr').find((r) => r.text().includes('合计'))
    expect(summaryRow).toBeTruthy()
    expect(summaryRow.text()).toContain('1')
    expect(summaryRow.text()).toContain('100')

    wrapper.unmount()
  })

  it('刷新后分区汇总与缺口明细同步重拉', async () => {
    const wrapper = await mountPage()
    expect(getStockGapZoneSummary).toHaveBeenCalledTimes(1)

    const refreshBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('刷新'))
    await refreshBtn.trigger('click')
    await flushPromises()

    expect(getStockGaps).toHaveBeenCalledTimes(2)
    expect(getStockGapZoneSummary).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('库存变更后分区汇总随明细一起重算', async () => {
    const wrapper = await mountPage()

    getStockGapZoneSummary.mockResolvedValueOnce([
      {
        zoneTagId: 2,
        zoneTagName: '线缆布线区',
        unassignedZone: false,
        shortageAccessoryCount: 0,
        gapQuantityTotal: 0
      }
    ])
    notifyStockChanged('accessory')
    await flushPromises()

    expect(getStockGapZoneSummary).toHaveBeenCalledTimes(2)
    const card = wrapper.find('.zone-summary-card')
    const summaryRow = card.findAll('tr').find((r) => r.text().includes('合计'))
    expect(summaryRow.text()).toContain('0')

    wrapper.unmount()
  })
})

describe('库存缺口分析 - 导出分区汇总', () => {
  let clickSpy
  let capturedDownload

  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    getStockGapZoneSummary.mockResolvedValue([])
    capturedDownload = null
    // jsdom 未实现 Blob URL API，补齐为可断言的 mock
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn(() => 'blob:mock-url')
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: vi.fn()
    })
    clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      capturedDownload = this.download
    })
  })

  const findExportButton = (wrapper) =>
    wrapper.findAll('button').find((b) => b.text().includes('导出分区汇总'))

  const zoneSummaryDisposition = (encodedName) =>
    `attachment; filename="stock-gap-zone-summary-20260912.csv"; filename*=UTF-8''${encodedName}`

  it('正常导出：使用后端中文文件名下载并提示成功', async () => {
    const wrapper = await mountPage()
    exportStockGapZoneSummary.mockResolvedValue({
      headers: { 'content-disposition': zoneSummaryDisposition(encodeURIComponent('库存缺口分区汇总_20260912.csv')) },
      data: new Blob(['csv内容'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(exportStockGapZoneSummary).toHaveBeenCalledTimes(1)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(capturedDownload).toBe('库存缺口分区汇总_20260912.csv')
    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')

    wrapper.unmount()
  })

  it('响应未携带文件名时使用中文兜底文件名', async () => {
    const wrapper = await mountPage()
    exportStockGapZoneSummary.mockResolvedValue({
      headers: {},
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(capturedDownload).toMatch(/^库存缺口分区汇总_\d{8}\.csv$/)

    wrapper.unmount()
  })

  it('分区汇总为空时不发起导出并提示', async () => {
    getStockGaps.mockResolvedValue([])
    getStockGapZoneSummary.mockResolvedValue([])
    getWiringPlanPage.mockResolvedValue({ records: [], total: 0 })
    const wrapper = mount(StockGapList, {
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('暂无分区汇总数据可导出')
    expect(exportStockGapZoneSummary).not.toHaveBeenCalled()
    expect(clickSpy).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('导出过程中重复点击被忽略，完成后按钮恢复可用', async () => {
    const wrapper = await mountPage()
    let resolveExport
    exportStockGapZoneSummary.mockImplementation(
      () => new Promise((resolve) => { resolveExport = resolve })
    )

    const button = findExportButton(wrapper)
    await button.trigger('click')
    await flushPromises()
    expect(exportStockGapZoneSummary).toHaveBeenCalledTimes(1)
    expect(button.classes()).toContain('is-loading')

    // 请求未返回时连续点击（包括按钮 disabled 场景下的直接触发）：被忽略
    await button.trigger('click')
    await wrapper.vm.handleExportZoneSummary()
    expect(exportStockGapZoneSummary).toHaveBeenCalledTimes(1)

    resolveExport({
      headers: { 'content-disposition': zoneSummaryDisposition(encodeURIComponent('库存缺口分区汇总_20260912.csv')) },
      data: new Blob(['csv'], { type: 'text/csv' })
    })
    await flushPromises()

    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(button.classes()).not.toContain('is-loading')

    // 完成后再次点击可以正常发起第二次导出
    await button.trigger('click')
    await flushPromises()
    expect(exportStockGapZoneSummary).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('导出失败：不触发下载、不提示成功、按钮恢复可用', async () => {
    const wrapper = await mountPage()
    exportStockGapZoneSummary.mockRejectedValue(new Error('网络错误'))

    const button = findExportButton(wrapper)
    await button.trigger('click')
    await flushPromises()

    expect(clickSpy).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(button.classes()).not.toContain('is-loading')

    wrapper.unmount()
  })
})
