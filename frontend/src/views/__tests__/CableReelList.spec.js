import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import CableReelList from '@/views/CableReelList.vue'
import {
  getCableReelPage,
  createCableReel,
  openCableReel,
  deductCableReel,
  deleteCableReel
} from '@/api/cableReel'
import { getAccessoryPage } from '@/api/accessory'
import { __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/cableReel', () => ({
  getCableReelPage: vi.fn(),
  createCableReel: vi.fn(),
  openCableReel: vi.fn(),
  deductCableReel: vi.fn(),
  deleteCableReel: vi.fn()
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

// 等待 Element Plus 对话框/抽屉的进入过渡完成
const waitTransition = async () => {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 400))
}

const accessoryPage = () => ({
  records: [
    { id: 5, accessoryName: 'RVV电源线', model: 'RVV-2*1.0', specUnit: 'm' },
    { id: 4, accessoryName: '六类网线', model: 'CAT6-UTP', specUnit: 'm' }
  ],
  total: 2
})

const reelPage = () => ({
  records: [
    {
      id: 10,
      reelNo: 'P-001',
      accessoryId: 5,
      accessoryName: 'RVV电源线',
      model: 'RVV-2*1.0',
      specUnit: 'm',
      remainingMeters: 170,
      status: 1,
      statusText: '已开盘',
      accessoryStockQuantity: 170,
      accessoryDeleted: false,
      stockMatched: true,
      openTime: '2026-09-12 10:30:00',
      createTime: '2026-09-12 10:00:00'
    },
    {
      id: 11,
      reelNo: 'P-002',
      accessoryId: 5,
      accessoryName: 'RVV电源线',
      model: 'RVV-2*1.0',
      specUnit: 'm',
      remainingMeters: 300,
      status: 0,
      statusText: '未开盘',
      accessoryStockQuantity: null,
      accessoryDeleted: false,
      stockMatched: null,
      openTime: null,
      createTime: '2026-09-12 11:00:00'
    },
    {
      id: 12,
      reelNo: 'P-003',
      accessoryId: 5,
      accessoryName: 'RVV电源线',
      model: 'RVV-2*1.0',
      specUnit: 'm',
      remainingMeters: 200,
      status: 1,
      statusText: '已开盘',
      accessoryStockQuantity: 100,
      accessoryDeleted: false,
      stockMatched: false,
      openTime: '2026-09-12 09:00:00',
      createTime: '2026-09-12 08:00:00'
    }
  ],
  total: 3
})

const mountPage = async () => {
  getCableReelPage.mockResolvedValue(reelPage())
  getAccessoryPage.mockResolvedValue(accessoryPage())
  const wrapper = mount(CableReelList, {
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

const findRowByReelNo = (wrapper, reelNo) =>
  wrapper.findAll('.el-table__row').find(r => r.text().includes(reelNo))

describe('整盘电源线建档 - 列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('挂载时加载盘档案与配件下拉', async () => {
    const wrapper = await mountPage()
    expect(getCableReelPage).toHaveBeenCalledTimes(1)
    expect(getAccessoryPage).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('展示盘号、绑定配件、盘上剩余与状态；未开盘显示未入账，已开盘显示档案米数与一致标记', async () => {
    const wrapper = await mountPage()

    const openedRow = findRowByReelNo(wrapper, 'P-001')
    expect(openedRow.text()).toContain('RVV电源线')
    expect(openedRow.text()).toContain('170')
    expect(openedRow.text()).toContain('已开盘')
    expect(openedRow.text()).toContain('一致')
    expect(openedRow.text()).not.toContain('未开盘，未入账')

    const unopenedRow = findRowByReelNo(wrapper, 'P-002')
    expect(unopenedRow.text()).toContain('未开盘')
    expect(unopenedRow.text()).toContain('未开盘，未入账')
    // 未开盘行操作是“开盘确认/删除”，没有“扣米”
    expect(unopenedRow.text()).toContain('开盘确认')
    expect(unopenedRow.text()).not.toContain('扣米')

    // 盘上剩余与档案不一致的已开盘盘红色标“不一致”
    const mismatchRow = findRowByReelNo(wrapper, 'P-003')
    expect(mismatchRow.text()).toContain('不一致')
    expect(mismatchRow.find('.mismatch-text').exists()).toBeTruthy()

    wrapper.unmount()
  })
})

describe('整盘电源线建档 - 建档窗取消不建档', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('点取消只关窗，不发起建档请求，盘不落库', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '整盘建档')
    await waitTransition()

    const dialog = findDialogByTitle('整盘电源线建档')
    expect(dialog).toBeTruthy()
    wrapper.vm.createForm.reelNo = 'P-CANCEL'
    wrapper.vm.createForm.accessoryId = 5
    await flushPromises()

    clickButtonByText(dialog, '取消')
    await waitTransition()

    expect(createCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('点右上角关闭（v-model=false）同样不建档', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '整盘建档')
    await waitTransition()

    wrapper.vm.createDialogVisible = false
    await waitTransition()

    expect(createCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('盘号为空时点确认被表单拦截，不发请求', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '整盘建档')
    await waitTransition()
    const dialog = findDialogByTitle('整盘电源线建档')

    wrapper.vm.createForm.reelNo = ''
    wrapper.vm.createForm.accessoryId = 5
    wrapper.vm.createForm.remainingMeters = 200
    await flushPromises()

    clickButtonByText(dialog, '确认建档')
    await flushPromises()

    expect(createCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('纯空格盘号被拦截', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '整盘建档')
    await waitTransition()
    const dialog = findDialogByTitle('整盘电源线建档')

    wrapper.vm.createForm.reelNo = '   '
    wrapper.vm.createForm.accessoryId = 5
    await flushPromises()

    clickButtonByText(dialog, '确认建档')
    await flushPromises()

    expect(createCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('填齐后确认：盘号去空格后提交，成功提示并关窗重拉', async () => {
    const wrapper = await mountPage()
    createCableReel.mockResolvedValue(99)
    clickButtonByText(wrapper.element, '整盘建档')
    await waitTransition()
    const dialog = findDialogByTitle('整盘电源线建档')

    wrapper.vm.createForm.reelNo = '  P-NEW-01  '
    wrapper.vm.createForm.accessoryId = 5
    wrapper.vm.createForm.remainingMeters = 250
    await flushPromises()

    clickButtonByText(dialog, '确认建档')
    await waitTransition()

    expect(createCableReel).toHaveBeenCalledWith({
      reelNo: 'P-NEW-01',
      accessoryId: 5,
      remainingMeters: 250
    })
    expect(ElMessage.success).toHaveBeenCalledWith(
      expect.stringContaining('建档成功')
    )
    expect(wrapper.vm.createDialogVisible).toBe(false)
    // 建档不改变档案米数，不广播库存变更
    expect(getCableReelPage.mock.calls.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })
})

describe('整盘电源线建档 - 开盘确认', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('确认框确认后调用开盘接口并刷新', async () => {
    const wrapper = await mountPage()
    openCableReel.mockResolvedValue(undefined)
    ElMessageBox.confirm.mockImplementation(() => Promise.resolve())

    const unopenedRow = findRowByReelNo(wrapper, 'P-002')
    clickButtonByText(unopenedRow.element, '开盘确认')
    await waitTransition()

    expect(openCableReel).toHaveBeenCalledWith(11)
    expect(ElMessage.success).toHaveBeenCalledWith(
      expect.stringContaining('开盘成功')
    )
    await flushPromises()
    expect(getCableReelPage.mock.calls.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('确认框取消时不调用开盘接口', async () => {
    const wrapper = await mountPage()
    ElMessageBox.confirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    const unopenedRow = findRowByReelNo(wrapper, 'P-002')
    clickButtonByText(unopenedRow.element, '开盘确认')
    await waitTransition()

    expect(openCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('整盘电源线建档 - 扣米', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('打开扣米窗显示盘上剩余与配件档案米数，取消不发请求', async () => {
    const wrapper = await mountPage()
    const openedRow = findRowByReelNo(wrapper, 'P-001')
    clickButtonByText(openedRow.element, '扣米')
    await waitTransition()

    const dialog = findDialogByTitle('线缆盘扣米')
    expect(dialog).toBeTruthy()
    expect(dialog.textContent).toContain('P-001')
    expect(dialog.textContent).toContain('盘上剩余')
    expect(dialog.textContent).toContain('170')

    clickButtonByText(dialog, '取消')
    await waitTransition()
    expect(deductCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('确认扣米：按米数调用接口，成功后关窗重拉', async () => {
    const wrapper = await mountPage()
    deductCableReel.mockResolvedValue(undefined)
    const openedRow = findRowByReelNo(wrapper, 'P-001')
    clickButtonByText(openedRow.element, '扣米')
    await waitTransition()
    const dialog = findDialogByTitle('线缆盘扣米')

    wrapper.vm.deductForm.meters = 30
    await flushPromises()
    clickButtonByText(dialog, '确认扣米')
    await waitTransition()

    expect(deductCableReel).toHaveBeenCalledWith(10, 30)
    expect(ElMessage.success).toHaveBeenCalledWith('已扣减 30 米')
    expect(wrapper.vm.deductDialogVisible).toBe(false)
    wrapper.unmount()
  })

  it('扣减米数超过盘上剩余时被拦截，不发请求', async () => {
    const wrapper = await mountPage()
    const openedRow = findRowByReelNo(wrapper, 'P-001')
    clickButtonByText(openedRow.element, '扣米')
    await waitTransition()
    const dialog = findDialogByTitle('线缆盘扣米')

    wrapper.vm.deductForm.meters = 999
    await flushPromises()
    clickButtonByText(dialog, '确认扣米')
    await flushPromises()

    expect(deductCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('盘上剩余与档案不一致时扣米被拦截', async () => {
    const wrapper = await mountPage()
    const mismatchRow = findRowByReelNo(wrapper, 'P-003')
    clickButtonByText(mismatchRow.element, '扣米')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith(
      expect.stringContaining('不一致')
    )
    expect(deductCableReel).not.toHaveBeenCalled()
    expect(findDialogByTitle('线缆盘扣米')).toBeFalsy()
    wrapper.unmount()
  })
})

describe('整盘电源线建档 - 删除', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('未开盘盘确认删除调用删除接口；已开盘盘没有删除按钮', async () => {
    const wrapper = await mountPage()
    deleteCableReel.mockResolvedValue(undefined)
    ElMessageBox.confirm.mockImplementation(() => Promise.resolve())

    // 已开盘行（P-001/P-003）不渲染删除按钮，只有未开盘的 P-002 有
    const openedRow = findRowByReelNo(wrapper, 'P-001')
    expect(openedRow.text()).not.toContain('删除')

    const unopenedRow = findRowByReelNo(wrapper, 'P-002')
    clickButtonByText(unopenedRow.element, '删除')
    await waitTransition()

    expect(deleteCableReel).toHaveBeenCalledWith(11)
    wrapper.unmount()
  })

  it('删除确认框取消时不发请求', async () => {
    const wrapper = await mountPage()
    ElMessageBox.confirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    const unopenedRow = findRowByReelNo(wrapper, 'P-002')
    clickButtonByText(unopenedRow.element, '删除')
    await waitTransition()

    expect(deleteCableReel).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
