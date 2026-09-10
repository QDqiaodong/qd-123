import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import WiringPlanList from '@/views/WiringPlanList.vue'
import {
  getWiringPlanPage,
  updateWiringPlanStatus
} from '@/api/wiringPlan'
import { getAccessoryPage } from '@/api/accessory'

vi.mock('@/api/wiringPlan', () => ({
  getWiringPlanPage: vi.fn(),
  getWiringPlanById: vi.fn(),
  addWiringPlan: vi.fn(),
  updateWiringPlan: vi.fn(),
  deleteWiringPlan: vi.fn(),
  updateWiringPlanStatus: vi.fn()
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
