import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import WiringPlanList from '@/views/WiringPlanList.vue'
import {
  getWiringPlanPage,
  getWiringPlanById,
  updateWiringPlanStatus,
  writeoffWiringPlan
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
  getStockGaps: vi.fn()
}))

vi.mock('@/api/accessory', () => ({
  getAccessoryPage: vi.fn()
}))

// 保留真实组件，仅拦截消息提示便于断言
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
  {
    id: 1,
    planName: '厂区外围监控布线方案',
    scene: '厂区外围监控',
    detailCount: 4,
    status: 1,
    createTime: '2026-09-01 10:00:00'
  },
  {
    id: 2,
    planName: '机房网络布线方案',
    scene: '机房布线',
    detailCount: 4,
    status: 0,
    createTime: '2026-09-02 10:00:00'
  }
]

const mountPage = async () => {
  getWiringPlanPage.mockResolvedValue({ records: planRows(), total: 2 })
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

// 找到指定方案行内的开关
const switchOf = (wrapper, planId) => {
  const row = wrapper
    .findAll('.el-table__row')
    .find((r) => r.text().includes(planId === 1 ? '厂区外围监控布线方案' : '机房网络布线方案'))
  expect(row, `未找到方案 ${planId} 所在行`).toBeTruthy()
  return row.find('.el-switch')
}

describe('布线方案列表 - 状态开关', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('停用：开关从启用切到停用，调用接口并提示已停用', async () => {
    updateWiringPlanStatus.mockResolvedValue(undefined)
    const wrapper = await mountPage()
    const sw = switchOf(wrapper, 1)
    expect(sw.classes()).toContain('is-checked')

    await sw.trigger('click')
    await flushPromises()

    expect(updateWiringPlanStatus).toHaveBeenCalledTimes(1)
    expect(updateWiringPlanStatus).toHaveBeenCalledWith(1, 0)
    expect(ElMessage.success).toHaveBeenCalledWith('已停用')
    expect(sw.classes()).not.toContain('is-checked')
  })

  it('启用：开关从停用切到启用，调用接口并提示已启用', async () => {
    updateWiringPlanStatus.mockResolvedValue(undefined)
    const wrapper = await mountPage()
    const sw = switchOf(wrapper, 2)
    expect(sw.classes()).not.toContain('is-checked')

    await sw.trigger('click')
    await flushPromises()

    expect(updateWiringPlanStatus).toHaveBeenCalledTimes(1)
    expect(updateWiringPlanStatus).toHaveBeenCalledWith(2, 1)
    expect(ElMessage.success).toHaveBeenCalledWith('已启用')
    expect(sw.classes()).toContain('is-checked')
  })

  it('无效方案 ID：接口报错后开关恢复原状态，且不显示成功提示', async () => {
    updateWiringPlanStatus.mockRejectedValue(new Error('布线方案不存在或已被删除'))
    const wrapper = await mountPage()
    const sw = switchOf(wrapper, 1)
    expect(sw.classes()).toContain('is-checked')

    await sw.trigger('click')
    await flushPromises()

    expect(updateWiringPlanStatus).toHaveBeenCalledWith(1, 0)
    // 开关恢复为原状态（启用）
    expect(sw.classes()).toContain('is-checked')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('停用失败同样恢复原状态', async () => {
    updateWiringPlanStatus.mockRejectedValue(new Error('布线方案不存在或已被删除'))
    const wrapper = await mountPage()
    const sw = switchOf(wrapper, 2)
    expect(sw.classes()).not.toContain('is-checked')

    await sw.trigger('click')
    await flushPromises()

    expect(updateWiringPlanStatus).toHaveBeenCalledWith(2, 1)
    // 开关恢复为原状态（停用）
    expect(sw.classes()).not.toContain('is-checked')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('连续快速切换：请求未返回时忽略重复点击，恢复后仍可切换', async () => {
    let resolveFirst
    updateWiringPlanStatus
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValue(undefined)

    const wrapper = await mountPage()
    const sw = switchOf(wrapper, 1)

    // 第一次点击：发起停用请求（请求挂起，开关进入 loading）
    await sw.trigger('click')
    expect(updateWiringPlanStatus).toHaveBeenCalledTimes(1)
    expect(sw.classes()).toContain('is-disabled')

    // 请求未返回时连续点击：被忽略，不产生新请求
    await sw.trigger('click')
    await sw.trigger('click')
    expect(updateWiringPlanStatus).toHaveBeenCalledTimes(1)

    // 第一个请求完成后开关恢复可用
    resolveFirst()
    await flushPromises()
    expect(sw.classes()).not.toContain('is-disabled')
    expect(ElMessage.success).toHaveBeenCalledWith('已停用')

    // 再次点击可以正常发起下一次切换
    await sw.trigger('click')
    await flushPromises()
    expect(updateWiringPlanStatus).toHaveBeenCalledTimes(2)
    expect(updateWiringPlanStatus).toHaveBeenLastCalledWith(1, 1)
    expect(ElMessage.success).toHaveBeenCalledWith('已启用')
  })
})

describe('布线方案详情 - 分区分组展示', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 后端按分区排序号升序、同分区按配件名称排序后的返回结果
  const detailPlan = () => ({
    id: 1,
    planName: '厂区外围监控布线方案',
    scene: '厂区外围监控',
    status: 1,
    createTime: '2026-09-01 10:00:00',
    description: null,
    detailCount: 5,
    details: [
      { id: 10, accessoryId: 100, accessoryName: 'RVV电源线', model: 'RVV-2×1.0', quantity: 600, zoneTagId: 2, zoneTagName: '线缆布线区' },
      { id: 14, accessoryId: 103, accessoryName: '六类网线', model: 'CAT6', quantity: 800, zoneTagId: 2, zoneTagName: '线缆布线区' },
      { id: 11, accessoryId: 101, accessoryName: '防爆摄像头', model: 'FB-200', quantity: 12, zoneTagId: 5, zoneTagName: '监控设备区' },
      { id: 13, accessoryId: 999, accessoryName: '配件已删除', model: null, quantity: 3, zoneTagId: null, zoneTagName: null },
      { id: 12, accessoryId: 102, accessoryName: '扎带', model: 'ZD-100', quantity: 300, zoneTagId: null, zoneTagName: null }
    ]
  })

  const openDetail = async (wrapper, plan) => {
    getWiringPlanById.mockResolvedValue(plan)
    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes(plan.planName))
    const viewBtn = row.findAll('button').find((b) => b.text().includes('详情'))
    await viewBtn.trigger('click')
    await flushPromises()
  }

  it('分区分组按后端返回顺序渲染，未分配分区在最后', async () => {
    const wrapper = await mountPage()
    await openDetail(wrapper, detailPlan())

    // 分组顺序与后端排序结果一致：线缆布线区 -> 监控设备区 -> 未分配分区
    const headers = wrapper.findAll('.zone-group-header .el-tag')
      .map((el) => el.text().trim())
    expect(headers).toEqual(['线缆布线区', '监控设备区', '未分配分区'])

    // 同一分区内配件顺序与后端返回一致（按配件名称稳定排序）
    const groups = wrapper.findAll('.zone-group')
    const firstGroupNames = groups[0].findAll('tbody tr td:first-child')
      .map((el) => el.text().trim())
    expect(firstGroupNames).toEqual(['RVV电源线', '六类网线'])

    // 已删除配件与未分配分区的配件归入最后一组，删除配件展示兜底文案
    const lastGroupText = groups[2].text()
    expect(lastGroupText).toContain('配件已删除')
    expect(lastGroupText).toContain('扎带')

    wrapper.unmount()
  })

  it('空明细方案展示空状态提示', async () => {
    const wrapper = await mountPage()
    await openDetail(wrapper, { ...detailPlan(), detailCount: 0, details: [] })

    expect(wrapper.find('.zone-detail-section .el-empty').exists()).toBe(true)
    expect(wrapper.findAll('.zone-group').length).toBe(0)

    wrapper.unmount()
  })
})

describe('布线方案 - 核销出库', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const stockRows = () => [
    {
      id: 1,
      planName: '库存充足方案',
      scene: '厂区外围监控',
      detailCount: 1,
      status: 1,
      writeoff: false,
      stockSufficient: true,
      hasDeletedAccessory: false,
      createTime: '2026-09-01 10:00:00'
    },
    {
      id: 2,
      planName: '库存不足方案',
      scene: '机房布线',
      detailCount: 1,
      status: 1,
      writeoff: false,
      stockSufficient: false,
      hasDeletedAccessory: false,
      createTime: '2026-09-02 10:00:00'
    },
    {
      id: 3,
      planName: '已核销方案',
      scene: '室内监控',
      detailCount: 1,
      status: 1,
      writeoff: true,
      stockSufficient: true,
      hasDeletedAccessory: false,
      writeoffTime: '2026-09-10 12:00:00',
      createTime: '2026-09-03 10:00:00'
    }
  ]

  const mountStockPage = async (rows = stockRows()) => {
    getWiringPlanPage.mockResolvedValue({ records: rows, total: rows.length })
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

  const writeoffButtonOf = (wrapper, planName) => {
    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes(planName))
    expect(row, `未找到方案 ${planName} 所在行`).toBeTruthy()
    return row.findAll('button').find((b) => b.text().includes('核销出库'))
  }

  it('库存充足：确认后调用核销接口、提示成功并刷新列表', async () => {
    const wrapper = await mountStockPage()
    ElMessageBox.confirm.mockResolvedValue(undefined)
    writeoffWiringPlan.mockResolvedValue(undefined)
    getWiringPlanPage.mockResolvedValueOnce({ records: stockRows(), total: 3 })

    const btn = writeoffButtonOf(wrapper, '库存充足方案')
    expect(btn.attributes('disabled')).toBeUndefined()
    await btn.trigger('click')
    await flushPromises()

    expect(writeoffWiringPlan).toHaveBeenCalledTimes(1)
    expect(writeoffWiringPlan).toHaveBeenCalledWith(1)
    expect(ElMessage.success).toHaveBeenCalledWith('核销出库成功，现存量已扣减')
    // 核销成功后重新拉取列表
    expect(getWiringPlanPage).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('库存不足：核销按钮禁用，不调用接口', async () => {
    const wrapper = await mountStockPage()
    const btn = writeoffButtonOf(wrapper, '库存不足方案')
    expect(btn.attributes('disabled')).toBeDefined()
    await btn.trigger('click')
    await flushPromises()

    expect(writeoffWiringPlan).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已核销方案：按钮禁用且不可再编辑', async () => {
    const wrapper = await mountStockPage()

    const writeoffBtn = writeoffButtonOf(wrapper, '已核销方案')
    expect(writeoffBtn.attributes('disabled')).toBeDefined()

    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('已核销方案'))
    const editBtn = row.findAll('button').find((b) => b.text().includes('编辑'))
    expect(editBtn.attributes('disabled')).toBeDefined()
    expect(row.text()).toContain('已核销')

    wrapper.unmount()
  })

  it('已核销方案：启用状态开关不可拨动，误操作时明确提示且不调用接口', async () => {
    const wrapper = await mountStockPage()
    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('已核销方案'))
    const sw = row.find('.el-switch')
    // 已核销方案当前为启用状态
    expect(sw.classes()).toContain('is-checked')

    await sw.trigger('click')
    await flushPromises()

    // 开关保持原状态，不发起状态变更请求，并给出明确提示
    expect(sw.classes()).toContain('is-checked')
    expect(updateWiringPlanStatus).not.toHaveBeenCalled()
    expect(ElMessage.warning).toHaveBeenCalledWith('该方案已核销出库，现存量已扣减，不可变更启用状态')
    expect(ElMessage.success).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('停用方案不展示库存不足，核销按钮禁用', async () => {
    const rows = [
      {
        id: 4,
        planName: '停用方案',
        scene: '临时布线',
        detailCount: 1,
        status: 0,
        writeoff: false,
        stockSufficient: true,
        hasDeletedAccessory: false,
        createTime: '2026-09-04 10:00:00'
      }
    ]
    const wrapper = await mountStockPage(rows)
    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('停用方案'))
    expect(row.text()).toContain('停用不合计')
    const btn = writeoffButtonOf(wrapper, '停用方案')
    expect(btn.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('含已删除配件的方案：详情弹窗标红且核销按钮禁用', async () => {
    const wrapper = await mountStockPage([
      {
        id: 5,
        planName: '含失效配件方案',
        scene: '监控',
        detailCount: 1,
        status: 1,
        writeoff: false,
        stockSufficient: false,
        hasDeletedAccessory: true,
        createTime: '2026-09-05 10:00:00'
      }
    ])
    const plan = {
      id: 5,
      planName: '含失效配件方案',
      scene: '监控',
      status: 1,
      writeoff: false,
      stockSufficient: false,
      hasDeletedAccessory: true,
      createTime: '2026-09-05 10:00:00',
      description: null,
      detailCount: 1,
      details: [
        {
          id: 90,
          accessoryId: 999,
          accessoryName: '配件已删除',
          model: null,
          quantity: 3,
          stockQuantity: null,
          accessoryDeleted: true,
          zoneTagId: null,
          zoneTagName: null
        }
      ]
    }
    getWiringPlanById.mockResolvedValue(plan)
    const row = wrapper
      .findAll('.el-table__row')
      .find((r) => r.text().includes('含失效配件方案'))
    await row.findAll('button').find((b) => b.text().includes('详情')).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('无法核销出库')
    expect(wrapper.text()).toContain('不可核销')
    const footerBtn = wrapper
      .findAll('.el-dialog button')
      .filter((b) => b.text().includes('核销出库'))[0]
    expect(footerBtn.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })
})
