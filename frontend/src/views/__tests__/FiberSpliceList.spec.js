import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import FiberSpliceList from '@/views/FiberSpliceList.vue'
import {
  getFiberSplicePage,
  createFiberSplice,
  updateFiberSplice,
  commissionFiberSplice,
  voidFiberSplice
} from '@/api/fiberSplice'
import { getZoneTagList } from '@/api/zoneTag'

vi.mock('@/api/fiberSplice', () => ({
  getFiberSplicePage: vi.fn(),
  createFiberSplice: vi.fn(),
  updateFiberSplice: vi.fn(),
  commissionFiberSplice: vi.fn(),
  voidFiberSplice: vi.fn()
}))

vi.mock('@/api/zoneTag', () => ({
  getZoneTagList: vi.fn()
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

const waitTransition = async () => {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 400))
}

const zones = () => ([
  { id: 1, tagName: '弱电桥架区', sortOrder: 1 },
  { id: 2, tagName: '线缆布线区', sortOrder: 2 },
  { id: 3, tagName: '接头终端区', sortOrder: 3 }
])

const jointPage = () => ({
  records: [
    {
      id: 1,
      spliceNo: 'RJ-001',
      zoneTagId: 3,
      zoneName: '接头终端区',
      zoneDeleted: false,
      reserveMeters: 15,
      otdrPassed: 1,
      otdrPassedText: '已过 OTDR',
      commissionable: 1,
      commissionableText: '可投运',
      status: 0,
      statusText: '在档',
      remark: '',
      createTime: '2026-09-14 10:00:00'
    },
    {
      id: 2,
      spliceNo: 'RJ-002',
      zoneTagId: 3,
      zoneName: '接头终端区',
      zoneDeleted: false,
      reserveMeters: 20,
      otdrPassed: 0,
      otdrPassedText: '未过 OTDR',
      commissionable: 0,
      commissionableText: '不可投运',
      status: 0,
      statusText: '在档',
      remark: '',
      createTime: '2026-09-14 11:00:00'
    },
    {
      id: 3,
      spliceNo: 'RJ-003',
      zoneTagId: 2,
      zoneName: '线缆布线区',
      zoneDeleted: false,
      reserveMeters: 8,
      otdrPassed: 1,
      otdrPassedText: '已过 OTDR',
      commissionable: 0,
      commissionableText: '不可投运',
      status: 1,
      statusText: '已作废',
      voidReason: '熔接质量异常',
      voidTime: '2026-09-14 15:00:00',
      remark: '',
      createTime: '2026-09-14 09:00:00'
    }
  ],
  total: 3
})

const mountPage = async () => {
  getFiberSplicePage.mockResolvedValue(jointPage())
  getZoneTagList.mockResolvedValue(zones())
  const wrapper = mount(FiberSpliceList, {
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

describe('光纤熔接接头登记 - 列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('挂载时加载列表（默认只列在档筛选参数由后端处理）与分区下拉', async () => {
    const wrapper = await mountPage()
    expect(getFiberSplicePage).toHaveBeenCalledTimes(1)
    expect(getZoneTagList).toHaveBeenCalledTimes(1)
    // 默认档案状态为“在档”
    expect(getFiberSplicePage.mock.calls[0][0]).toMatchObject({ status: 0 })
    wrapper.unmount()
  })

  it('展示接头编号、所属分区、盘留米数、OTDR 与可投运状态', async () => {
    const wrapper = await mountPage()

    const passed = findRowByNo(wrapper, 'RJ-001')
    expect(passed.text()).toContain('接头终端区')
    expect(passed.text()).toContain('15')
    expect(passed.text()).toContain('已过 OTDR')
    expect(passed.text()).toContain('可投运')
    expect(passed.text()).toContain('取消可投运')

    // 未过 OTDR 的在档接头：不可投运，且“标可投运”按钮禁用（未过不能标可投运）
    const notPassed = findRowByNo(wrapper, 'RJ-002')
    expect(notPassed.text()).toContain('未过 OTDR')
    expect(notPassed.text()).toContain('不可投运')
    const commissionBtn = Array.from(notPassed.element.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('标可投运'))
    expect(commissionBtn).toBeTruthy()
    expect(commissionBtn.disabled).toBe(true)

    wrapper.unmount()
  })

  it('按所属分区筛选：选择分区后带 zoneTagId 重拉并回到第一页', async () => {
    const wrapper = await mountPage()
    wrapper.vm.searchForm.zoneTagId = 2
    wrapper.vm.handleSearch()
    await flushPromises()

    expect(getFiberSplicePage).toHaveBeenLastCalledWith(
      expect.objectContaining({ zoneTagId: 2, pageNum: 1 })
    )
    wrapper.unmount()
  })

  it('重置清空全部筛选并把档案状态恢复为在档', async () => {
    const wrapper = await mountPage()
    wrapper.vm.searchForm.keyword = 'RJ'
    wrapper.vm.searchForm.zoneTagId = 2
    wrapper.vm.searchForm.otdrPassed = 1
    wrapper.vm.handleReset()
    await flushPromises()

    expect(wrapper.vm.searchForm).toMatchObject({
      keyword: '',
      zoneTagId: null,
      otdrPassed: null,
      commissionable: null,
      status: 0
    })
    wrapper.unmount()
  })
})

describe('光纤熔接接头登记 - 登记/编辑', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('取消登记只关窗，不发请求', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '登记接头')
    await waitTransition()
    const dialog = findDialogByTitle('登记光纤熔接接头')
    expect(dialog).toBeTruthy()

    wrapper.vm.form.spliceNo = 'RJ-NEW'
    wrapper.vm.form.zoneTagId = 3
    await flushPromises()
    clickButtonByText(dialog, '取消')
    await waitTransition()

    expect(createFiberSplice).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('接头编号为空时被表单拦截，不发请求', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '登记接头')
    await waitTransition()
    const dialog = findDialogByTitle('登记光纤熔接接头')

    wrapper.vm.form.spliceNo = '   '
    wrapper.vm.form.zoneTagId = 3
    wrapper.vm.form.reserveMeters = 12
    await flushPromises()
    clickButtonByText(dialog, '确认登记')
    await flushPromises()

    expect(createFiberSplice).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('填齐后确认登记：编号去空格提交，新登记不带可投运标记，成功后关窗重拉', async () => {
    const wrapper = await mountPage()
    createFiberSplice.mockResolvedValue(99)
    clickButtonByText(wrapper.element, '登记接头')
    await waitTransition()
    const dialog = findDialogByTitle('登记光纤熔接接头')

    wrapper.vm.form.spliceNo = '  RJ-NEW-01  '
    wrapper.vm.form.zoneTagId = 3
    wrapper.vm.form.reserveMeters = 18
    wrapper.vm.form.otdrPassed = 0
    await flushPromises()
    clickButtonByText(dialog, '确认登记')
    await waitTransition()

    expect(createFiberSplice).toHaveBeenCalledWith({
      spliceNo: 'RJ-NEW-01',
      zoneTagId: 3,
      reserveMeters: 18,
      otdrPassed: 0,
      remark: ''
    })
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('登记成功'))
    expect(wrapper.vm.formDialogVisible).toBe(false)
    wrapper.unmount()
  })

  it('编辑时接头编号禁用不可改，保存走更新接口', async () => {
    const wrapper = await mountPage()
    updateFiberSplice.mockResolvedValue(undefined)

    const row = findRowByNo(wrapper, 'RJ-002')
    clickButtonByText(row.element, '编辑')
    await waitTransition()
    const dialog = findDialogByTitle('编辑接头 RJ-002')
    expect(dialog).toBeTruthy()

    const noInput = dialog.querySelector('input[disabled]')
    expect(noInput).toBeTruthy()
    expect(noInput.value).toContain('RJ-002')

    wrapper.vm.form.otdrPassed = 1
    wrapper.vm.form.reserveMeters = 22
    await flushPromises()
    clickButtonByText(dialog, '保存修改')
    await waitTransition()

    expect(updateFiberSplice).toHaveBeenCalledWith(2, {
      zoneTagId: 3,
      reserveMeters: 22,
      otdrPassed: 1,
      remark: ''
    })
    wrapper.unmount()
  })
})

describe('光纤熔接接头登记 - 可投运', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('已过 OTDR 但尚未可投运的接头点“标可投运”调用接口', async () => {
    // RJ-003 已作废无按钮；本用例改一条已过OTDR不可投运的数据
    getFiberSplicePage.mockResolvedValue({
      records: [{
        id: 5, spliceNo: 'RJ-005', zoneTagId: 2, zoneName: '线缆布线区', zoneDeleted: false,
        reserveMeters: 10, otdrPassed: 1, otdrPassedText: '已过 OTDR',
        commissionable: 0, commissionableText: '不可投运', status: 0, statusText: '在档',
        remark: '', createTime: '2026-09-14 10:00:00'
      }],
      total: 1
    })
    getZoneTagList.mockResolvedValue(zones())
    commissionFiberSplice.mockResolvedValue(undefined)
    const wrapper = mount(FiberSpliceList, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], components: { ...Icons } }
    })
    await flushPromises()

    const row = findRowByNo(wrapper, 'RJ-005')
    const btn = Array.from(row.element.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('标可投运'))
    expect(btn.disabled).toBe(false)
    btn.click()
    await flushPromises()

    expect(commissionFiberSplice).toHaveBeenCalledWith(5, 1)
    wrapper.unmount()
  })

  it('取消可投运调用接口传 0', async () => {
    const wrapper = await mountPage()
    commissionFiberSplice.mockResolvedValue(undefined)

    const row = findRowByNo(wrapper, 'RJ-001')
    clickButtonByText(row.element, '取消可投运')
    await flushPromises()

    expect(commissionFiberSplice).toHaveBeenCalledWith(1, 0)
    wrapper.unmount()
  })
})

describe('光纤熔接接头登记 - 作废留档', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('作废弹窗取消不发请求', async () => {
    const wrapper = await mountPage()
    const row = findRowByNo(wrapper, 'RJ-002')
    clickButtonByText(row.element, '作废')
    await waitTransition()
    const dialog = findDialogByTitle('作废光纤熔接接头')
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toContain('不会物理删除')

    clickButtonByText(dialog, '取消')
    await waitTransition()
    expect(voidFiberSplice).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('确认作废带原因调用作废接口（无删除接口被调用）', async () => {
    const wrapper = await mountPage()
    voidFiberSplice.mockResolvedValue(undefined)

    const row = findRowByNo(wrapper, 'RJ-002')
    clickButtonByText(row.element, '作废')
    await waitTransition()
    const dialog = findDialogByTitle('作废光纤熔接接头')

    wrapper.vm.voidReason = '重复登记'
    await flushPromises()
    clickButtonByText(dialog, '确认作废留档')
    await waitTransition()

    expect(voidFiberSplice).toHaveBeenCalledWith(2, '重复登记')
    expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('已作废留档'))
    wrapper.unmount()
  })

  it('已作废行只展示留档标记，没有任何操作按钮', async () => {
    const wrapper = await mountPage()
    const row = findRowByNo(wrapper, 'RJ-003')
    expect(row.text()).toContain('已作废')
    expect(row.text()).toContain('熔接质量异常')
    expect(row.text()).toContain('已留档，只读')
    for (const text of ['编辑', '标可投运', '取消可投运', '作废']) {
      const btn = Array.from(row.element.querySelectorAll('button'))
        .find(b => b.textContent.replace(/\s/g, '').includes(text))
      expect(btn).toBeFalsy()
    }
    wrapper.unmount()
  })
})
