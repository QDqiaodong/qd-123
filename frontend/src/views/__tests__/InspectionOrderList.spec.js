import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import InspectionOrderList from '@/views/InspectionOrderList.vue'
import {
  getInspectionPage,
  createInspection,
  writeInspectionResult,
  qualifyInspection
} from '@/api/inspectionOrder'
import { getAccessoryPage } from '@/api/accessory'

vi.mock('@/api/inspectionOrder', () => ({
  getInspectionPage: vi.fn(),
  createInspection: vi.fn(),
  writeInspectionResult: vi.fn(),
  qualifyInspection: vi.fn()
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
    }
  }
})

const STORAGE_KEY = 'inspection-order-filters'

const waitTransition = async () => {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 400))
}

const accessories = () => ({
  records: [
    { id: 11, accessoryName: '防爆摄像头', model: 'DS-2CD3T46', specUnit: 'MP' },
    { id: 6, accessoryName: 'BNC接头', model: 'BNC-75-5', specUnit: 'mm' }
  ],
  total: 2
})

const orderPage = () => ({
  records: [
    {
      id: 1,
      inspectionNo: 'SJ20260914100001001',
      accessoryId: 11,
      accessoryName: '防爆摄像头',
      model: 'DS-2CD3T46',
      specUnit: 'MP',
      batchNo: 'B2026-09',
      labName: '厂区中心实验室',
      sampleReturned: 0,
      sampleReturnedText: '待回样',
      qualified: 0,
      qualifiedText: '待回样，不可判定',
      labConclusion: null,
      sampleReturnTime: null,
      qualifiedTime: null,
      createTime: '2026-09-14 09:00:00'
    },
    {
      id: 2,
      inspectionNo: 'SJ20260914100002002',
      accessoryId: 6,
      accessoryName: 'BNC接头',
      model: 'BNC-75-5',
      specUnit: 'mm',
      batchNo: 'B2026-08',
      labName: '第三方检测机构',
      sampleReturned: 1,
      sampleReturnedText: '已回样',
      qualified: 0,
      qualifiedText: '未判定合格',
      labConclusion: '电气性能符合标准',
      sampleReturnTime: '2026-09-14 12:00:00',
      qualifiedTime: null,
      createTime: '2026-09-13 09:00:00'
    },
    {
      id: 3,
      inspectionNo: 'SJ20260914100003003',
      accessoryId: 6,
      accessoryName: 'BNC接头',
      model: 'BNC-75-5',
      specUnit: 'mm',
      batchNo: 'B2026-07',
      labName: '厂区中心实验室',
      sampleReturned: 1,
      sampleReturnedText: '已回样',
      qualified: 1,
      qualifiedText: '合格',
      labConclusion: '检测合格',
      sampleReturnTime: '2026-09-12 12:00:00',
      qualifiedTime: '2026-09-12 15:00:00',
      createTime: '2026-09-11 09:00:00'
    }
  ],
  total: 3
})

const mountPage = async () => {
  getInspectionPage.mockResolvedValue(orderPage())
  getAccessoryPage.mockResolvedValue(accessories())
  const wrapper = mount(InspectionOrderList, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

const clickButtonByText = (root, text) => {
  const btn = Array.from(root.querySelectorAll('button'))
    .find(b => b.textContent.replace(/\s/g, '').includes(text))
  if (!btn) throw new Error(`未找到按钮：${text}`)
  btn.click()
  return btn
}

const findDialogByTitle = (title) =>
  Array.from(document.querySelectorAll('.el-dialog'))
    .find(d => d.textContent.includes(title))

const findRowByNo = (wrapper, no) =>
  wrapper.findAll('.el-table__row').find(r => r.text().includes(no))

describe('辅材送检单 - 列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    localStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
  })

  it('挂载时加载送检单与已建档配件下拉', async () => {
    const wrapper = await mountPage()
    expect(getInspectionPage).toHaveBeenCalledTimes(1)
    expect(getAccessoryPage).toHaveBeenCalledTimes(1)
    expect(getAccessoryPage.mock.calls[0][0]).toMatchObject({ pageNum: 1, pageSize: 1000 })

    const pending = findRowByNo(wrapper, 'SJ20260914100001001')
    expect(pending.text()).toContain('B2026-09')
    expect(pending.text()).toContain('厂区中心实验室')
    expect(pending.text()).toContain('待回样')
    expect(pending.text()).toContain('待回样后写回')

    // 待回样行只有“写回结论”，没有标合格入口（未回样不能标合格）
    expect(pending.text()).not.toContain('标合格')
    const writeBtn = Array.from(pending.element.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('写回结论'))
    expect(writeBtn).toBeTruthy()

    // 已回样未判定行展示结论并提供“标合格”
    const returned = findRowByNo(wrapper, 'SJ20260914100002002')
    expect(returned.text()).toContain('已回样')
    expect(returned.text()).toContain('电气性能符合标准')
    const qualifyBtn = Array.from(returned.element.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('标合格'))
    expect(qualifyBtn).toBeTruthy()
    expect(qualifyBtn.disabled).toBe(false)

    // 已合格行显示“取消合格”
    const qualified = findRowByNo(wrapper, 'SJ20260914100003003')
    expect(qualified.text()).toContain('合格')
    expect(qualified.text()).toContain('取消合格')

    wrapper.unmount()
  })

  it('按是否已回样筛选：选择后带 sampleReturned 重拉并回到第一页，同时写入 localStorage', async () => {
    const wrapper = await mountPage()
    wrapper.vm.searchForm.sampleReturned = 1
    wrapper.vm.handleSearch()
    await flushPromises()

    expect(getInspectionPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ sampleReturned: 1, pageNum: 1 })
    )
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)).sampleReturned).toBe(1)
    wrapper.unmount()
  })

  it('刷新（重新挂载）后恢复已回样筛选条件并用该条件查询', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      keyword: 'BNC',
      sampleReturned: 0,
      qualified: null
    }))
    getInspectionPage.mockResolvedValue({ records: [], total: 0 })
    getAccessoryPage.mockResolvedValue(accessories())
    const wrapper = mount(InspectionOrderList, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    expect(wrapper.vm.searchForm).toMatchObject({ keyword: 'BNC', sampleReturned: 0 })
    expect(getInspectionPage.mock.calls[0][0]).toMatchObject({
      keyword: 'BNC',
      sampleReturned: 0
    })
    wrapper.unmount()
  })

  it('重置清空筛选并移除 localStorage', async () => {
    const wrapper = await mountPage()
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      keyword: 'BNC', sampleReturned: 1, qualified: 1
    }))
    wrapper.vm.handleReset()
    await flushPromises()

    expect(wrapper.vm.searchForm).toMatchObject({
      keyword: '',
      sampleReturned: null,
      qualified: null
    })
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
    wrapper.unmount()
  })
})

describe('辅材送检单 - 新建', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    localStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
  })

  it('取消新建只关窗，不发请求', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '新建送检单')
    await waitTransition()
    const dialog = findDialogByTitle('新建辅材送检单')
    expect(dialog).toBeTruthy()
    clickButtonByText(dialog, '取消')
    await waitTransition()
    expect(createInspection).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('送检批次或实验室未填完整时被表单拦截，不能提交', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '新建送检单')
    await waitTransition()
    const dialog = findDialogByTitle('新建辅材送检单')

    // 只选配件、填批次，实验室为纯空格
    wrapper.vm.createForm.accessoryId = 11
    wrapper.vm.createForm.batchNo = 'B2026-09'
    wrapper.vm.createForm.labName = '   '
    await flushPromises()
    clickButtonByText(dialog, '提交送检单')
    await waitTransition()
    expect(createInspection).not.toHaveBeenCalled()

    // 批次留空同样拦截
    wrapper.vm.createForm.labName = '厂区中心实验室'
    wrapper.vm.createForm.batchNo = ''
    await flushPromises()
    clickButtonByText(dialog, '提交送检单')
    await waitTransition()
    expect(createInspection).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('填齐配件、批次、实验室后提交：字段去空格，新建成功后关窗重拉', async () => {
    const wrapper = await mountPage()
    createInspection.mockResolvedValue(88)
    clickButtonByText(wrapper.element, '新建送检单')
    await waitTransition()
    const dialog = findDialogByTitle('新建辅材送检单')

    wrapper.vm.createForm.accessoryId = 11
    wrapper.vm.createForm.batchNo = '  B2026-09  '
    wrapper.vm.createForm.labName = '  厂区中心实验室  '
    await flushPromises()
    clickButtonByText(dialog, '提交送检单')
    await waitTransition()

    expect(createInspection).toHaveBeenCalledWith({
      accessoryId: 11,
      batchNo: 'B2026-09',
      labName: '厂区中心实验室'
    })
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('待回样'))
    expect(wrapper.vm.createDialogVisible).toBe(false)
    wrapper.unmount()
  })
})

describe('辅材送检单 - 回样与合格', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
    localStorage.clear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
  })

  it('写回结论弹窗取消不发请求', async () => {
    const wrapper = await mountPage()
    const row = findRowByNo(wrapper, 'SJ20260914100001001')
    clickButtonByText(row.element, '写回结论')
    await waitTransition()
    const dialog = findDialogByTitle('实验室回样结论写回')
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toContain('B2026-09')
    clickButtonByText(dialog, '取消')
    await waitTransition()
    expect(writeInspectionResult).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('结论为空时确认按钮禁用；填齐后调用写回接口', async () => {
    const wrapper = await mountPage()
    writeInspectionResult.mockResolvedValue(undefined)
    const row = findRowByNo(wrapper, 'SJ20260914100001001')
    clickButtonByText(row.element, '写回结论')
    await waitTransition()
    const dialog = findDialogByTitle('实验室回样结论写回')

    const confirmBtn = Array.from(dialog.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('确认回样'))
    expect(confirmBtn.disabled).toBe(true)

    wrapper.vm.labConclusion = '  检测合格，准予使用  '
    await flushPromises()
    expect(confirmBtn.disabled).toBe(false)
    clickButtonByText(dialog, '确认回样')
    await waitTransition()

    expect(writeInspectionResult).toHaveBeenCalledWith(1, '检测合格，准予使用')
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('已回样'))
    wrapper.unmount()
  })

  it('已回样行点“标合格”调用接口传 1，合格行“取消合格”传 0', async () => {
    const wrapper = await mountPage()
    qualifyInspection.mockResolvedValue(undefined)

    const returned = findRowByNo(wrapper, 'SJ20260914100002002')
    clickButtonByText(returned.element, '标合格')
    await flushPromises()
    expect(qualifyInspection).toHaveBeenCalledWith(2, 1)

    const qualified = findRowByNo(wrapper, 'SJ20260914100003003')
    clickButtonByText(qualified.element, '取消合格')
    await flushPromises()
    expect(qualifyInspection).toHaveBeenCalledWith(3, 0)
    wrapper.unmount()
  })
})
