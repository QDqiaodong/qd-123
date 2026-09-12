import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import SafetyStockList from '@/views/SafetyStockList.vue'
import { getSafetyStockShortages, exportSafetyStockShortages } from '@/api/accessory'
import { ElMessage } from 'element-plus'
import { notifyStockChanged, __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/accessory', () => ({
  getSafetyStockShortages: vi.fn(),
  exportSafetyStockShortages: vi.fn()
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

// 按 label 选中 el-select 选项（element-plus 下拉需先展开再点选项）
const selectOption = async (wrapper, label) => {
  await wrapper.find('.zone-filter').find('input').trigger('mousedown')
  await flushPromises()
  const options = document.querySelectorAll('.el-select-dropdown__item')
  const target = Array.from(options).find((el) => el.textContent.trim() === label)
  expect(target).toBeTruthy()
  target.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

describe('安全库存台账', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  it('挂载时加载台账，列出名称/分区/现存量/下限/缺口', async () => {
    const wrapper = await mountPage()
    expect(getSafetyStockShortages).toHaveBeenCalledTimes(1)
    // 全集加载不带筛选参数
    expect(getSafetyStockShortages).toHaveBeenCalledWith({})

    const text = wrapper.text()
    expect(text).toContain('六类网线')
    expect(text).toContain('线缆布线区')
    expect(text).toContain('600')
    expect(text).toContain('800')
    expect(text).toContain('200')

    wrapper.unmount()
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

  it('分区下拉包含全部分区、台账内各分区与固定的未分配分区', async () => {
    const wrapper = await mountPage()
    await wrapper.find('.zone-filter').find('input').trigger('mousedown')
    await flushPromises()
    const labels = Array.from(document.querySelectorAll('.el-select-dropdown__item')).map((el) =>
      el.textContent.trim()
    )
    expect(labels).toEqual(['全部分区', '弱电桥架区', '线缆布线区', '未分配分区'])

    wrapper.unmount()
  })

  it('选择具体分区后按 zoneTagId 重新拉取，页面只显示该分区数据', async () => {
    const wrapper = await mountPage()
    getSafetyStockShortages.mockClear()
    getSafetyStockShortages.mockResolvedValue([
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
      }
    ])

    await selectOption(wrapper, '线缆布线区')

    expect(getSafetyStockShortages).toHaveBeenCalledWith({ zoneTagId: 2 })
    // 只显示线缆布线区一行；未分区分组在具体分区视图下隐藏
    expect(wrapper.findAll('.assigned-table tbody tr').length).toBe(1)
    expect(wrapper.find('.unassigned-section').exists()).toBe(false)
    expect(wrapper.find('.summary-tags').text()).toContain('线缆布线区待补货 1 种')
    expect(wrapper.find('.summary-tags').text()).toContain('缺口合计 200 件')
    expect(wrapper.text()).toContain('六类网线')
    expect(wrapper.text()).not.toContain('镀锌桥架')

    wrapper.unmount()
  })

  it('未分配分区可单独筛出，只显示未分配低位件', async () => {
    const wrapper = await mountPage()
    getSafetyStockShortages.mockClear()
    getSafetyStockShortages.mockResolvedValue([
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

    await selectOption(wrapper, '未分配分区')

    expect(getSafetyStockShortages).toHaveBeenCalledWith({ unassignedZone: true })
    // 只看未分配分区：已分配主表隐藏，未分区分组变主表
    expect(wrapper.find('.assigned-table').exists()).toBe(false)
    const section = wrapper.find('.unassigned-section')
    expect(section.exists()).toBe(true)
    expect(section.findAll('tbody tr').length).toBe(1)
    expect(section.text()).toContain('未分配低位件')
    const summary = wrapper.find('.summary-tags').text()
    expect(summary).toContain('未分配分区待补货 1 种')
    expect(summary).toContain('缺口合计 5 件')
    // 只看未分配分区时不再重复“其中未分配分区”统计
    expect(summary).not.toContain('其中未分配分区')

    wrapper.unmount()
  })

  it('具体分区无低位件时已分配表显示该分区空状态', async () => {
    const wrapper = await mountPage()
    getSafetyStockShortages.mockClear()
    getSafetyStockShortages.mockResolvedValue([])

    await selectOption(wrapper, '弱电桥架区')

    expect(getSafetyStockShortages).toHaveBeenCalledWith({ zoneTagId: 1 })
    expect(wrapper.find('.assigned-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('该分区暂无低于下限的配件')
    expect(wrapper.find('.summary-tags').text()).toContain('缺口合计 0 件')

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

describe('安全库存台账 - 导出当前分区', () => {
  let clickSpy
  let capturedDownload

  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
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
    wrapper.findAll('button').find((b) => b.text().includes('导出当前分区'))

  const disposition = (encodedName) =>
    `attachment; filename="safety-stock-20260912.csv"; filename*=UTF-8''${encodedName}`

  it('全集导出：无筛选参数，使用后端中文文件名下载并提示成功', async () => {
    const wrapper = await mountPage()
    exportSafetyStockShortages.mockResolvedValue({
      headers: { 'content-disposition': disposition(encodeURIComponent('安全库存台账_20260912.csv')) },
      data: new Blob(['csv内容'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(exportSafetyStockShortages).toHaveBeenCalledTimes(1)
    // 全集导出参数与页面台账加载参数一致（无筛选）
    expect(exportSafetyStockShortages).toHaveBeenCalledWith({})
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(capturedDownload).toBe('安全库存台账_20260912.csv')
    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')

    wrapper.unmount()
  })

  it('换到具体分区后导出携带同一 zoneTagId，行数口径与页面一致', async () => {
    const wrapper = await mountPage()
    getSafetyStockShortages.mockResolvedValue([
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
      }
    ])
    await selectOption(wrapper, '线缆布线区')

    expect(wrapper.findAll('.assigned-table tbody tr').length).toBe(1)
    exportSafetyStockShortages.mockClear()
    exportSafetyStockShortages.mockResolvedValue({
      headers: { 'content-disposition': disposition(encodeURIComponent('安全库存台账_20260912.csv')) },
      data: new Blob(['名称,分区,现存量,下限,缺口\r\n六类网线,线缆布线区,600,800,200\r\n'], {
        type: 'text/csv'
      })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    // 导出与页面台账同源同筛选参数：导出行数、缺口跟页面一致
    expect(exportSafetyStockShortages).toHaveBeenCalledWith({ zoneTagId: 2 })
    expect(capturedDownload).toBe('安全库存台账_20260912.csv')

    wrapper.unmount()
  })

  it('筛选未分配分区后导出携带 unassignedZone=true', async () => {
    const wrapper = await mountPage()
    getSafetyStockShortages.mockResolvedValue([
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
    await selectOption(wrapper, '未分配分区')

    exportSafetyStockShortages.mockResolvedValue({
      headers: {},
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(exportSafetyStockShortages).toHaveBeenCalledWith({ unassignedZone: true })
    // 响应未带文件名时使用中文兜底文件名
    expect(capturedDownload).toMatch(/^安全库存台账_\d{8}\.csv$/)

    wrapper.unmount()
  })

  it('当前筛选无数据时不发起导出并提示', async () => {
    getSafetyStockShortages.mockResolvedValue([])
    const wrapper = mount(SafetyStockList, {
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('当前筛选暂无台账数据可导出')
    expect(exportSafetyStockShortages).not.toHaveBeenCalled()
    expect(clickSpy).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('导出过程中重复点击被忽略', async () => {
    const wrapper = await mountPage()
    let resolveExport
    exportSafetyStockShortages.mockImplementation(
      () => new Promise((resolve) => { resolveExport = resolve })
    )

    const button = findExportButton(wrapper)
    await button.trigger('click')
    await flushPromises()
    expect(exportSafetyStockShortages).toHaveBeenCalledTimes(1)

    await button.trigger('click')
    await wrapper.vm.handleExport()
    expect(exportSafetyStockShortages).toHaveBeenCalledTimes(1)

    resolveExport({
      headers: { 'content-disposition': disposition(encodeURIComponent('安全库存台账_20260912.csv')) },
      data: new Blob(['csv'], { type: 'text/csv' })
    })
    await flushPromises()

    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')

    wrapper.unmount()
  })
})
