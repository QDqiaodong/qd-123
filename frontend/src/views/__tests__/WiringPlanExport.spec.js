import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import WiringPlanList from '@/views/WiringPlanList.vue'
import {
  getWiringPlanPage,
  exportWiringPlans
} from '@/api/wiringPlan'
import { getAccessoryPage } from '@/api/accessory'

vi.mock('@/api/wiringPlan', () => ({
  getWiringPlanPage: vi.fn(),
  getWiringPlanById: vi.fn(),
  addWiringPlan: vi.fn(),
  updateWiringPlan: vi.fn(),
  deleteWiringPlan: vi.fn(),
  updateWiringPlanStatus: vi.fn(),
  writeoffWiringPlan: vi.fn(),
  getStockGaps: vi.fn(),
  exportWiringPlans: vi.fn()
}))

vi.mock('@/api/accessory', () => ({
  getAccessoryPage: vi.fn()
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn()
    },
    ElMessageBox: {
      confirm: vi.fn()
    }
  }
})

const planRows = () => [
  { id: 1, planName: '厂区外围监控布线方案', scene: '厂区外围监控', detailCount: 4, status: 1, createTime: '2026-09-01 10:00:00' }
]

const chineseDisposition = (encodedName) =>
  `attachment; filename="wiring-plan-export-20260910.csv"; filename*=UTF-8''${encodedName}`

const mountPage = async () => {
  getWiringPlanPage.mockResolvedValue({ records: planRows(), total: 1 })
  getAccessoryPage.mockResolvedValue({ records: [], total: 0 })
  const wrapper = mount(WiringPlanList, {
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

const findExportButton = (wrapper) =>
  wrapper.findAll('button').find((b) => b.text().includes('导出当前筛选结果'))

describe('布线方案列表 - 导出当前筛选结果', () => {
  let clickSpy
  let capturedDownload

  beforeEach(() => {
    vi.clearAllMocks()
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

  it('正常导出：携带当前筛选条件、使用中文文件名下载并提示成功', async () => {
    const wrapper = await mountPage()
    exportWiringPlans.mockResolvedValue({
      headers: { 'content-disposition': chineseDisposition(encodeURIComponent('布线方案导出_20260910.csv')) },
      data: new Blob(['csv内容'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    // 导出前按当前条件预检结果是否为空（仅取 1 条）
    const precheckCall = getWiringPlanPage.mock.calls.find(
      (args) => args[0] && args[0].pageSize === 1
    )
    expect(precheckCall).toBeTruthy()

    expect(exportWiringPlans).toHaveBeenCalledTimes(1)
    expect(exportWiringPlans).toHaveBeenCalledWith({ keyword: '', status: null })
    expect(clickSpy).toHaveBeenCalledTimes(1)
    expect(capturedDownload).toBe('布线方案导出_20260910.csv')
    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })

  it('空结果：预检为空时提示且不发起导出、不下载', async () => {
    const wrapper = await mountPage()
    // 预检调用（pageSize=1）返回空
    getWiringPlanPage.mockImplementation((params) =>
      params.pageSize === 1
        ? Promise.resolve({ records: [], total: 0 })
        : Promise.resolve({ records: planRows(), total: 1 })
    )

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('当前筛选条件下没有可导出的方案')
    expect(exportWiringPlans).not.toHaveBeenCalled()
    expect(clickSpy).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('导出过程中重复点击被忽略，完成后按钮恢复可用', async () => {
    const wrapper = await mountPage()
    let resolveExport
    exportWiringPlans.mockImplementation(
      () => new Promise((resolve) => { resolveExport = resolve })
    )

    const button = findExportButton(wrapper)
    await button.trigger('click')
    // 等待预检请求完成、导出请求发出
    await flushPromises()
    expect(exportWiringPlans).toHaveBeenCalledTimes(1)
    expect(button.classes()).toContain('is-loading')

    // 请求未返回时连续点击（包括按钮 disabled 场景下的直接触发）：被忽略
    await button.trigger('click')
    await wrapper.vm.handleExport()
    await wrapper.vm.handleExport()
    expect(exportWiringPlans).toHaveBeenCalledTimes(1)

    resolveExport({
      headers: { 'content-disposition': chineseDisposition(encodeURIComponent('布线方案导出_20260910.csv')) },
      data: new Blob(['csv'], { type: 'text/csv' })
    })
    await flushPromises()

    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    expect(button.classes()).not.toContain('is-loading')

    // 完成后再次点击可以正常发起第二次导出
    await button.trigger('click')
    await flushPromises()
    expect(exportWiringPlans).toHaveBeenCalledTimes(2)
  })

  it('导出携带页面上的关键词与启用状态筛选条件', async () => {
    const wrapper = await mountPage()
    exportWiringPlans.mockResolvedValue({
      headers: { 'content-disposition': chineseDisposition(encodeURIComponent('布线方案导出_20260910.csv')) },
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    wrapper.vm.searchForm.keyword = '监控'
    wrapper.vm.searchForm.status = 1

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(exportWiringPlans).toHaveBeenCalledWith({ keyword: '监控', status: 1 })
    const precheckCall = getWiringPlanPage.mock.calls.find(
      (args) => args[0] && args[0].pageSize === 1
    )[0]
    expect(precheckCall).toMatchObject({ keyword: '监控', status: 1 })
  })

  it('超长中文文件名：截断后仍保留 .csv 扩展名', async () => {
    const wrapper = await mountPage()
    const longName = '布线方案导出_'.padEnd(120, '超') + '.csv'
    exportWiringPlans.mockResolvedValue({
      headers: { 'content-disposition': chineseDisposition(encodeURIComponent(longName)) },
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(capturedDownload.length).toBeLessThanOrEqual(80)
    expect(capturedDownload.endsWith('.csv')).toBe(true)
  })

  it('响应未携带文件名时使用含筛选信息的中文兜底文件名', async () => {
    const wrapper = await mountPage()
    exportWiringPlans.mockResolvedValue({
      headers: {},
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    wrapper.vm.searchForm.keyword = '监控'
    wrapper.vm.searchForm.status = 0

    await findExportButton(wrapper).trigger('click')
    await flushPromises()

    expect(capturedDownload).toMatch(/^布线方案导出_监控_停用_\d{8}\.csv$/)
  })

  it('导出失败：不触发下载、不提示成功、按钮恢复可用', async () => {
    const wrapper = await mountPage()
    exportWiringPlans.mockRejectedValue(new Error('网络错误'))

    const button = findExportButton(wrapper)
    await button.trigger('click')
    await flushPromises()

    expect(clickSpy).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(button.classes()).not.toContain('is-loading')
  })
})
