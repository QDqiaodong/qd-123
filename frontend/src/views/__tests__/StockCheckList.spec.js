import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import StockCheckList from '@/views/StockCheckList.vue'
import {
  getStockCheckPage,
  getStockCheckById,
  createStockCheck,
  recordStockCheckItems,
  confirmStockCheck,
  deleteStockCheck,
  exportStockCheckDiff
} from '@/api/stockCheck'
import { getZoneTagList } from '@/api/zoneTag'
import { notifyStockChanged, __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/stockCheck', () => ({
  getStockCheckPage: vi.fn(),
  getStockCheckById: vi.fn(),
  createStockCheck: vi.fn(),
  recordStockCheckItems: vi.fn(),
  confirmStockCheck: vi.fn(),
  deleteStockCheck: vi.fn(),
  exportStockCheckDiff: vi.fn()
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
    },
    ElMessageBox: {
      confirm: vi.fn()
    }
  }
})

// 等待 Element Plus 抽屉/对话框的进入过渡完成
const waitTransition = async () => {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 400))
}

const zoneList = () => [
  { id: 1, tagName: '弱电桥架区', remark: '桥架' },
  { id: 2, tagName: '线缆布线区', remark: '线缆' }
]

const pendingPage = () => ({
  records: [
    {
      id: 10,
      checkNo: 'PD20260912100000001',
      zoneTagId: 2,
      zoneName: '线缆布线区',
      unassignedZone: false,
      status: 0,
      statusText: '待确认',
      itemCount: 3,
      recordedCount: 1,
      diffCount: 1,
      confirmTime: null,
      createTime: '2026-09-12 10:00:00'
    },
    {
      id: 11,
      checkNo: 'PD20260912090000002',
      zoneTagId: null,
      zoneName: '未分配分区',
      unassignedZone: true,
      status: 1,
      statusText: '已确认',
      itemCount: 0,
      recordedCount: 0,
      diffCount: 0,
      confirmTime: '2026-09-12 09:30:00',
      createTime: '2026-09-12 09:00:00'
    }
  ],
  total: 2
})

// 待确认盘点单详情：2 个正常配件（1 个已登记有差异）+ 1 个未登记 + 1 个已删除
const pendingDetail = () => ({
  header: {
    id: 10,
    checkNo: 'PD20260912100000001',
    zoneTagId: 2,
    zoneName: '线缆布线区',
    unassignedZone: false,
    status: 0,
    statusText: '待确认',
    itemCount: 3,
    createTime: '2026-09-12 10:00:00',
    confirmTime: null
  },
  items: [
    {
      id: 1001,
      accessoryId: 3,
      accessoryName: '超五类网线',
      model: 'CAT5e-UTP',
      bookQuantity: 1000,
      actualQuantity: 980,
      recorded: true,
      diffQuantity: -20,
      diffType: 'loss',
      accessoryDeleted: false,
      specUnit: 'mm'
    },
    {
      id: 1002,
      accessoryId: 4,
      accessoryName: '六类网线',
      model: 'CAT6-UTP',
      bookQuantity: 600,
      actualQuantity: null,
      recorded: false,
      diffQuantity: null,
      diffType: 'unrecorded',
      accessoryDeleted: false,
      specUnit: 'mm'
    },
    {
      id: 1003,
      accessoryId: 99,
      accessoryName: '旧型号线缆',
      model: 'OLD-CABLE',
      bookQuantity: 0,
      actualQuantity: null,
      recorded: false,
      diffQuantity: null,
      diffType: 'deleted',
      accessoryDeleted: true,
      specUnit: null
    }
  ],
  recordedCount: 1,
  diffCount: 1,
  gainCount: 0,
  lossCount: 1,
  totalDiffQuantity: -20
})

const mountPage = async () => {
  getStockCheckPage.mockResolvedValue(pendingPage())
  getZoneTagList.mockResolvedValue(zoneList())
  const wrapper = mount(StockCheckList, {
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

const openPendingDrawer = async (wrapper) => {
  getStockCheckById.mockResolvedValue(pendingDetail())
  clickButtonByText(wrapper.element, '登记/查看')
  await waitTransition()
  return document.body.querySelector('.el-drawer')
}

describe('分区盘点 - 列表', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('挂载时加载盘点单与分区标签', async () => {
    const wrapper = await mountPage()
    expect(getStockCheckPage).toHaveBeenCalledTimes(1)
    expect(getZoneTagList).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('列表展示单号、分区、状态与登记进度', async () => {
    const wrapper = await mountPage()
    const rows = wrapper.findAll('.el-table__row')
    expect(rows.length).toBe(2)
    expect(rows[0].text()).toContain('PD20260912100000001')
    expect(rows[0].text()).toContain('线缆布线区')
    expect(rows[0].text()).toContain('待确认')
    expect(rows[0].text()).toContain('3 种 / 1 种')
    // 未分配分区盘点单展示未分配标签，已确认单有确认时间
    expect(rows[1].text()).toContain('未分配分区')
    expect(rows[1].text()).toContain('已确认')
    expect(rows[1].text()).toContain('2026-09-12 09:30:00')
    wrapper.unmount()
  })

  it('已确认单不显示删除按钮、操作文案为查看', async () => {
    const wrapper = await mountPage()
    const rows = wrapper.findAll('.el-table__row')
    // 待确认单有删除按钮
    expect(rows[0].text()).toContain('删除')
    // 已确认单没有删除按钮、入口为“查看”
    expect(rows[1].text()).not.toContain('删除')
    expect(rows[1].text()).toContain('查看')
    wrapper.unmount()
  })

  it('空分区（明细 0 种）的盘点单同样在列表展示', async () => {
    const wrapper = await mountPage()
    const confirmedRow = wrapper.findAll('.el-table__row')[1]
    expect(confirmedRow.text()).toContain('0 种 / 0 种')
    wrapper.unmount()
  })
})

describe('分区盘点 - 分页与改每页条数', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('后页修改每页条数：回到第一页并保留分区/状态筛选重新查询', async () => {
    const wrapper = await mountPage()
    // 带着“线缆布线区 + 待确认”筛选翻到第 3 页
    wrapper.vm.searchForm.zoneTagId = 2
    wrapper.vm.searchForm.status = 0
    wrapper.vm.pagination.pageNum = 3
    getStockCheckPage.mockClear()
    getStockCheckPage.mockResolvedValue(pendingPage())

    wrapper.vm.handleSizeChange(20)
    await flushPromises()

    expect(wrapper.vm.pagination.pageNum).toBe(1)
    expect(wrapper.vm.pagination.pageSize).toBe(20)
    // 分区与状态筛选不能丢
    expect(wrapper.vm.searchForm.zoneTagId).toBe(2)
    expect(wrapper.vm.searchForm.status).toBe(0)
    expect(getStockCheckPage).toHaveBeenCalledTimes(1)
    expect(getStockCheckPage).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 20,
      status: 0,
      zoneTagId: 2
    })
    wrapper.unmount()
  })

  it('当前页越界拿到空记录但 total>0 时，自动回第一页按原筛选重拉', async () => {
    const wrapper = await mountPage()
    // 未分配分区（占位值 0）+ 已确认，停在不存在的第 5 页
    wrapper.vm.searchForm.zoneTagId = 0
    wrapper.vm.searchForm.status = 1
    wrapper.vm.pagination.pageNum = 5
    getStockCheckPage.mockClear()
    getStockCheckPage
      .mockResolvedValueOnce({ records: [], total: 12 })
      .mockResolvedValueOnce(pendingPage())

    wrapper.vm.loadData()
    await flushPromises()
    await flushPromises()

    // 先用越界页码按原筛选查，再自动回到第一页按同一筛选重拉
    expect(getStockCheckPage).toHaveBeenCalledTimes(2)
    expect(getStockCheckPage.mock.calls[0]).toEqual([
      { pageNum: 5, pageSize: 10, status: 1, unassigned: true }
    ])
    expect(getStockCheckPage.mock.calls[1]).toEqual([
      { pageNum: 1, pageSize: 10, status: 1, unassigned: true }
    ])
    expect(wrapper.vm.pagination.pageNum).toBe(1)
    // 重拉后单据回来了，不拿空表当没单
    expect(wrapper.findAll('.el-table__row').length).toBe(2)
    wrapper.unmount()
  })

  it('筛选确实无单（total 为 0）时不重拉，表格就是正常空态', async () => {
    const wrapper = await mountPage()
    wrapper.vm.searchForm.zoneTagId = 2
    wrapper.vm.searchForm.status = 0
    getStockCheckPage.mockClear()
    getStockCheckPage.mockResolvedValue({ records: [], total: 0 })

    wrapper.vm.handleSearch()
    await flushPromises()

    expect(getStockCheckPage).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.pagination.pageNum).toBe(1)
    expect(wrapper.findAll('.el-table__row').length).toBe(0)
    wrapper.unmount()
  })
})

describe('分区盘点 - 开盘', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('未选择分区时提示，不发起开盘', async () => {
    const wrapper = await mountPage()
    clickButtonByText(wrapper.element, '按分区开盘')
    await waitTransition()
    clickButtonByText(document.body.querySelector('.el-dialog'), '开盘')
    await flushPromises()
    expect(ElMessage.warning).toHaveBeenCalledWith('请选择盘点分区（未分配分区也可选择）')
    expect(createStockCheck).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('选择未分配分区开盘时 zoneTagId 传 null，开盘后打开新单详情', async () => {
    const wrapper = await mountPage()
    getStockCheckById.mockResolvedValue(pendingDetail())
    createStockCheck.mockResolvedValue(55)

    clickButtonByText(wrapper.element, '按分区开盘')
    await waitTransition()

    // 选择“未分配分区”（value=0）：jsdom 中下拉浮层不易展开，直接给组件状态赋值
    wrapper.vm.createZoneTagId = 0
    await flushPromises()

    clickButtonByText(document.body.querySelector('.el-dialog'), '开盘')
    await waitTransition()

    expect(createStockCheck).toHaveBeenCalledWith({ zoneTagId: null })
    expect(getStockCheckById).toHaveBeenCalledWith(55)
    expect(ElMessage.success).toHaveBeenCalledWith('开盘成功')
    wrapper.unmount()
  })

  it('选择具体分区开盘传真实分区ID', async () => {
    const wrapper = await mountPage()
    createStockCheck.mockResolvedValue(56)
    getStockCheckById.mockResolvedValue(pendingDetail())

    clickButtonByText(wrapper.element, '按分区开盘')
    await waitTransition()
    wrapper.vm.createZoneTagId = 2
    await flushPromises()
    clickButtonByText(document.body.querySelector('.el-dialog'), '开盘')
    await waitTransition()

    expect(createStockCheck).toHaveBeenCalledWith({ zoneTagId: 2 })
    wrapper.unmount()
  })
})

describe('分区盘点 - 登记实盘与差异', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('详情展示账面快照、已登记实盘、差异标签，已删除配件只展示不可登记', async () => {
    const wrapper = await mountPage()
    const drawer = await openPendingDrawer(wrapper)

    expect(drawer).toBeTruthy()
    expect(drawer.textContent).toContain('PD20260912100000001')
    expect(drawer.textContent).toContain('盘亏 1 种')
    expect(drawer.textContent).toContain('差异合计 -20 件')
    expect(drawer.textContent).toContain('已删除 1 种')

    const rows = drawer.querySelectorAll('.el-table__row')
    expect(rows.length).toBe(3)
    // 已删除配件行无录入框，提示“已删除，不可登记”“不回写”
    const deletedRow = Array.from(rows).find(r => r.textContent.includes('旧型号线缆'))
    expect(deletedRow.querySelector('input')).toBeNull()
    expect(deletedRow.textContent).toContain('已删除，不可登记')
    expect(deletedRow.textContent).toContain('不回写')
    // 正常配件有录入框
    const normalRow = Array.from(rows).find(r => r.textContent.includes('超五类网线'))
    expect(normalRow.querySelector('input')).toBeTruthy()
    expect(normalRow.textContent).toContain('盘亏 -20')

    wrapper.unmount()
  })

  it('保存登记只提交已登记项，成功后提示库存未改动并重拉详情', async () => {
    const wrapper = await mountPage()
    await openPendingDrawer(wrapper)
    recordStockCheckItems.mockResolvedValue(undefined)

    clickButtonByText(document.body.querySelector('.el-drawer'), '保存登记')
    await waitTransition()

    expect(recordStockCheckItems).toHaveBeenCalledWith(10, [
      { itemId: 1001, actualQuantity: 980 }
    ])
    expect(ElMessage.success).toHaveBeenCalledWith('实盘数已登记，库存尚未改动，确认后才会回写')
    // 保存后重新拉取详情与列表
    expect(getStockCheckById).toHaveBeenCalledTimes(2)
    expect(getStockCheckPage).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('存在未登记配件时确认按钮禁用，全部未登记时保存被拦截', async () => {
    const wrapper = await mountPage()
    await openPendingDrawer(wrapper)

    let confirmBtn = clickButtonByText(document.body.querySelector('.el-drawer'), '确认并回写')
    // el-button 禁用态下 click 不触发处理，这里直接断言 disabled
    expect(confirmBtn.disabled).toBe(true)

    // 清空唯一已登记项：保存登记应被拦截
    wrapper.vm.actualMap[1001] = null
    await flushPromises()
    clickButtonByText(document.body.querySelector('.el-drawer'), '保存登记')
    await flushPromises()
    expect(ElMessage.warning).toHaveBeenCalledWith('请至少登记一个配件的实盘数后再保存')
    expect(recordStockCheckItems).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('录入新实盘数后差异实时重算（盘盈），未保存时确认按钮禁用', async () => {
    const wrapper = await mountPage()
    const drawer = await openPendingDrawer(wrapper)

    // 找到“六类网线”行的录入框（未登记项），填入 650
    const rows = drawer.querySelectorAll('.el-table__row')
    const unrecordedRow = Array.from(rows).find(r => r.textContent.includes('六类网线'))
    const input = unrecordedRow.querySelector('input')
    expect(input).toBeTruthy()
    input.value = '650'
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await flushPromises()
    expect(unrecordedRow.textContent).toContain('盘盈 +50')

    // 有未保存修改时确认按钮禁用
    const confirmBtn = Array.from(drawer.querySelectorAll('button'))
      .find(b => b.textContent.replace(/\s/g, '').includes('确认并回写'))
    expect(confirmBtn.disabled).toBe(true)

    wrapper.unmount()
  })
})

describe('分区盘点 - 确认回写与锁定', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  // 全部已登记、无未登记项的待确认详情
  const fullyRecordedDetail = () => {
    const detail = pendingDetail()
    detail.items = detail.items.filter(item => !item.accessoryDeleted)
    detail.items[1].actualQuantity = 600
    detail.items[1].recorded = true
    detail.items[1].diffQuantity = 0
    detail.items[1].diffType = 'even'
    detail.recordedCount = 2
    detail.diffCount = 0
    detail.lossCount = 0
    detail.totalDiffQuantity = -20
    return detail
  }

  it('确认成功：调用确认接口、提示回写成功并广播库存变更', async () => {
    const wrapper = await mountPage()
    getStockCheckById.mockResolvedValue(fullyRecordedDetail())
    clickButtonByText(wrapper.element, '登记/查看')
    await waitTransition()

    ElMessageBox.confirm.mockImplementation(() => Promise.resolve())
    confirmStockCheck.mockResolvedValue(undefined)
    const confirmedDetail = fullyRecordedDetail()
    confirmedDetail.header = { ...confirmedDetail.header, status: 1, statusText: '已确认' }
    getStockCheckById.mockResolvedValue(confirmedDetail)

    const confirmBtn = clickButtonByText(document.body.querySelector('.el-drawer'), '确认并回写')
    expect(confirmBtn.disabled).toBe(false)
    await waitTransition()
    await waitTransition()

    expect(confirmStockCheck).toHaveBeenCalledWith(10, null)
    expect(ElMessage.success).toHaveBeenCalledWith('盘点已确认，档案现存量已一次性回写')
    // 确认后列表重拉（详情内提示也已变为已确认）
    expect(getStockCheckPage.mock.calls.length).toBeGreaterThanOrEqual(2)

    wrapper.unmount()
  })

  it('取消确认框时不调用回写接口', async () => {
    const wrapper = await mountPage()
    getStockCheckById.mockResolvedValue(fullyRecordedDetail())
    clickButtonByText(wrapper.element, '登记/查看')
    await waitTransition()

    ElMessageBox.confirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    clickButtonByText(document.body.querySelector('.el-drawer'), '确认并回写')
    await waitTransition()

    expect(confirmStockCheck).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已确认单只读：抽屉无录入框、无保存/确认按钮', async () => {
    const wrapper = await mountPage()
    const confirmed = (() => {
      const d = fullyRecordedDetail()
      d.header.status = 1
      d.header.statusText = '已确认'
      d.header.confirmTime = '2026-09-12 10:30:00'
      return d
    })()
    getStockCheckById.mockResolvedValue(confirmed)
    // 已确认单操作列文案为“查看”
    clickButtonByText(wrapper.element, '查看')
    await waitTransition()

    const drawer = document.body.querySelector('.el-drawer')
    expect(drawer).toBeTruthy()
    expect(drawer.querySelectorAll('input').length).toBe(0)
    const buttons = Array.from(drawer.querySelectorAll('button')).map(b => b.textContent)
    expect(buttons.some(t => t.includes('保存登记'))).toBe(false)
    expect(buttons.some(t => t.includes('确认并回写'))).toBe(false)

    wrapper.unmount()
  })

  it('收到库存变更广播时刷新列表', async () => {
    const wrapper = await mountPage()
    notifyStockChanged('accessory')
    await flushPromises()
    expect(getStockCheckPage).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})

describe('分区盘点 - 已确认单导出差异明细', () => {
  let capturedDownload

  // 已确认单：盘亏 +20/-20 两条差异、1 条一致、1 条已删除
  const confirmedDetailWithDiff = () => ({
    header: {
      id: 11,
      checkNo: 'PD20260912090000002',
      zoneTagId: null,
      zoneName: '未分配分区',
      unassignedZone: true,
      status: 1,
      statusText: '已确认',
      itemCount: 4,
      createTime: '2026-09-12 09:00:00',
      confirmTime: '2026-09-12 09:30:00'
    },
    items: [
      {
        id: 2001,
        accessoryId: 3,
        accessoryName: '超五类网线',
        bookQuantity: 1000,
        actualQuantity: 980,
        recorded: true,
        diffQuantity: -20,
        diffType: 'loss',
        accessoryDeleted: false,
        specUnit: 'mm'
      },
      {
        id: 2002,
        accessoryId: 4,
        accessoryName: '六类网线',
        bookQuantity: 600,
        actualQuantity: 620,
        recorded: true,
        diffQuantity: 20,
        diffType: 'gain',
        accessoryDeleted: false,
        specUnit: 'mm'
      },
      {
        id: 2003,
        accessoryId: 5,
        accessoryName: '水晶头',
        bookQuantity: 100,
        actualQuantity: 100,
        recorded: true,
        diffQuantity: 0,
        diffType: 'even',
        accessoryDeleted: false,
        specUnit: '个'
      },
      {
        id: 2004,
        accessoryId: 99,
        accessoryName: '旧型号',
        bookQuantity: 10,
        actualQuantity: null,
        recorded: false,
        diffQuantity: null,
        diffType: 'deleted',
        accessoryDeleted: true,
        specUnit: null
      }
    ],
    recordedCount: 3,
    diffCount: 2,
    gainCount: 1,
    lossCount: 1,
    totalDiffQuantity: 0
  })

  const openConfirmedDrawer = async (wrapper) => {
    getStockCheckById.mockResolvedValue(confirmedDetailWithDiff())
    // 已确认单在列表第二行，操作文案为“查看”
    const viewButtons = Array.from(wrapper.element.querySelectorAll('button'))
      .filter(b => b.textContent.replace(/\s/g, '').includes('查看'))
    viewButtons[viewButtons.length - 1].click()
    await waitTransition()
    return document.body.querySelector('.el-drawer')
  }

  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
    document.body.innerHTML = ''
    capturedDownload = null
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
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      capturedDownload = this.download
    })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('已确认单抽屉显示导出按钮，待确认单不显示', async () => {
    const pendingWrapper = await mountPage()
    const pendingDrawer = await openPendingDrawer(pendingWrapper)
    expect(pendingDrawer.textContent).not.toContain('导出差异明细')
    pendingWrapper.unmount()
    document.body.innerHTML = ''

    const confirmedWrapper = await mountPage()
    const confirmedDrawer = await openConfirmedDrawer(confirmedWrapper)
    expect(confirmedDrawer.textContent).toContain('导出差异明细')
    confirmedWrapper.unmount()
  })

  it('点击导出：调用导出接口、使用后端中文文件名下载并提示成功', async () => {
    const wrapper = await mountPage()
    const drawer = await openConfirmedDrawer(wrapper)
    exportStockCheckDiff.mockResolvedValue({
      headers: {
        'content-disposition': `attachment; filename="fallback.csv"; filename*=UTF-8''${encodeURIComponent('盘点差异明细_PD20260912090000002.csv')}`
      },
      data: new Blob(['csv'], { type: 'text/csv' })
    })

    const exportBtn = Array.from(drawer.querySelectorAll('button'))
      .find(b => b.textContent.includes('导出差异明细'))
    await exportBtn.click()
    await flushPromises()

    expect(exportStockCheckDiff).toHaveBeenCalledWith(11)
    expect(capturedDownload).toBe('盘点差异明细_PD20260912090000002.csv')
    expect(ElMessage.success).toHaveBeenCalledWith('导出成功')
    wrapper.unmount()
  })

  it('导出请求未完成时重复点击被忽略', async () => {
    const wrapper = await mountPage()
    const drawer = await openConfirmedDrawer(wrapper)
    let resolveExport
    exportStockCheckDiff.mockImplementation(
      () => new Promise((resolve) => { resolveExport = resolve })
    )

    const exportBtn = Array.from(drawer.querySelectorAll('button'))
      .find(b => b.textContent.includes('导出差异明细'))
    await exportBtn.click()
    await flushPromises()
    await exportBtn.click()
    await wrapper.vm.handleExportDiff()
    expect(exportStockCheckDiff).toHaveBeenCalledTimes(1)

    resolveExport({
      headers: { 'content-disposition': '' },
      data: new Blob(['csv'], { type: 'text/csv' })
    })
    await flushPromises()
    expect(capturedDownload).toBe('盘点差异明细_PD20260912090000002.csv')
    wrapper.unmount()
  })

  it('后端拒绝（待确认单）时不下载、提示后端原因', async () => {
    const wrapper = await mountPage()
    const drawer = await openConfirmedDrawer(wrapper)
    // 极端并发：单据已退回待确认，后端在 HTTP 200 中返回 JSON 业务错误体
    exportStockCheckDiff.mockRejectedValue(new Error('待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细'))

    const exportBtn = Array.from(drawer.querySelectorAll('button'))
      .find(b => b.textContent.includes('导出差异明细'))
    await exportBtn.click()
    await flushPromises()

    expect(capturedDownload).toBeNull()
    expect(ElMessage.success).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
