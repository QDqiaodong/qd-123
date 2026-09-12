import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as Icons from '@element-plus/icons-vue'
import AccessoryList from '@/views/AccessoryList.vue'
import {
  getAccessoryPage,
  addAccessory,
  updateAccessory,
  updateAccessoryZone
} from '@/api/accessory'
import { getZoneTagList } from '@/api/zoneTag'
import { __resetStockListenersForTests } from '@/utils/stockSync'

vi.mock('@/api/accessory', () => ({
  getAccessoryPage: vi.fn(),
  addAccessory: vi.fn(),
  updateAccessory: vi.fn(),
  deleteAccessory: vi.fn(),
  updateAccessoryZone: vi.fn()
}))

vi.mock('@/api/zoneTag', () => ({
  getZoneTagList: vi.fn()
}))

const accessoryRows = () => [
  // 六类网线：设了下限 800，现存 600 低于下限 → 待补货
  {
    id: 4,
    accessoryName: '六类网线',
    model: 'CAT6-UTP',
    material: '铜芯',
    scene: '千兆网络布线',
    specMin: null,
    specMax: null,
    specUnit: 'mm',
    zoneTagId: 2,
    stockQuantity: 600,
    safetyStock: 800,
    remark: ''
  },
  // 水晶头：设了下限但现存充足 → 不标红
  {
    id: 7,
    accessoryName: '水晶头',
    model: 'RJ45-8P8C',
    material: '镀金',
    scene: '网线终端',
    specMin: null,
    specMax: null,
    specUnit: 'mm',
    zoneTagId: 3,
    stockQuantity: 500,
    safetyStock: 100,
    remark: ''
  },
  // RVV电源线：未设下限 → 显示“未设置”，不监控
  {
    id: 5,
    accessoryName: 'RVV电源线',
    model: 'RVV-2*1.0',
    material: '铜芯',
    scene: '设备供电',
    specMin: null,
    specMax: null,
    specUnit: 'mm²',
    zoneTagId: 2,
    stockQuantity: 10,
    safetyStock: null,
    remark: ''
  }
]

const mountPage = async () => {
  getAccessoryPage.mockResolvedValue({ records: accessoryRows(), total: 3 })
  getZoneTagList.mockResolvedValue([
    { id: 2, tagName: '线缆布线区' },
    { id: 3, tagName: '接头终端区' }
  ])
  const wrapper = mount(AccessoryList, {
    global: {
      plugins: [ElementPlus],
      components: { ...Icons }
    }
  })
  await flushPromises()
  return wrapper
}

const findRowByName = (wrapper, name) =>
  wrapper.findAll('.el-table__row').find((r) => r.text().includes(name))

describe('配件档案 - 安全库存下限', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetStockListenersForTests()
  })

  it('列表展示下限：低于下限标“待补货”，未设置显示占位', async () => {
    const wrapper = await mountPage()

    const lowRow = findRowByName(wrapper, '六类网线')
    expect(lowRow.text()).toContain('800')
    const lowCell = lowRow.find('.safety-low')
    expect(lowCell.exists()).toBe(true)
    expect(lowCell.text()).toContain('待补货')

    const enoughRow = findRowByName(wrapper, '水晶头')
    expect(enoughRow.find('.safety-low').exists()).toBe(false)
    expect(enoughRow.text()).toContain('100')

    const unsetRow = findRowByName(wrapper, 'RVV电源线')
    expect(unsetRow.find('.safety-unset').text()).toContain('未设置')
    expect(unsetRow.find('.safety-low').exists()).toBe(false)

    wrapper.unmount()
  })

  it('编辑配件时回填并保存安全库存下限', async () => {
    updateAccessory.mockResolvedValue({})
    getAccessoryPage.mockResolvedValue({ records: accessoryRows(), total: 3 })
    const wrapper = await mountPage()

    const editBtn = findRowByName(wrapper, '六类网线')
      .findAll('button')
      .find((b) => b.text().includes('编辑'))
    await editBtn.trigger('click')
    await flushPromises()

    // el-input-number 回填为下限 800
    const numberInputs = wrapper.findAll('.el-dialog input').filter((i) => i.attributes('role') === 'spinbutton')
    // 规格最小/最大 + 现存量 + 安全库存下限
    const safetyInput = numberInputs[numberInputs.length - 1]
    expect(safetyInput.element.value).toContain('800')

    // 改成 900 并提交
    const setValue = (input, value) => {
      input.element.value = value
      input.trigger('input')
      input.trigger('change')
    }
    setValue(safetyInput, '900')
    await flushPromises()

    const confirmBtn = wrapper
      .findAll('.el-dialog button')
      .find((b) => b.text().includes('确定'))
    await confirmBtn.trigger('click')
    await flushPromises()

    expect(updateAccessory).toHaveBeenCalledTimes(1)
    const payload = updateAccessory.mock.calls[0][0]
    expect(payload.id).toBe(4)
    expect(payload.safetyStock).toBe(900)

    wrapper.unmount()
  })

  it('新增配件默认不带下限（不设下限不进台账）', async () => {
    addAccessory.mockResolvedValue({})
    getAccessoryPage.mockResolvedValue({ records: [], total: 0 })
    const wrapper = await mountPage()

    const addBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('新增配件'))
    await addBtn.trigger('click')
    await flushPromises()

    // 填必填项
    const dialog = wrapper.find('.el-dialog')
    const textInputs = dialog.findAll('input').filter((i) => i.attributes('role') !== 'spinbutton')
    await textInputs[0].setValue('测试配件')
    await textInputs[1].setValue('TEST-1')

    const confirmBtn = dialog
      .findAll('button')
      .find((b) => b.text().includes('确定'))
    await confirmBtn.trigger('click')
    await flushPromises()

    expect(addAccessory).toHaveBeenCalledTimes(1)
    const payload = addAccessory.mock.calls[0][0]
    expect(payload.safetyStock).toBeNull()

    wrapper.unmount()
  })

  it('“清空（不设下限）”按钮把下限置空', async () => {
    updateAccessory.mockResolvedValue({})
    getAccessoryPage.mockResolvedValue({ records: accessoryRows(), total: 3 })
    const wrapper = await mountPage()

    await findRowByName(wrapper, '六类网线')
      .findAll('button')
      .find((b) => b.text().includes('编辑'))
      .trigger('click')
    await flushPromises()

    const clearBtn = wrapper
      .findAll('.el-dialog button')
      .find((b) => b.text().includes('清空（不设下限）'))
    expect(clearBtn).toBeTruthy()
    await clearBtn.trigger('click')
    await flushPromises()

    const dialog = wrapper.find('.el-dialog')
    const numberInputs = dialog
      .findAll('input')
      .filter((i) => i.attributes('role') === 'spinbutton')
    const safetyInput = numberInputs[numberInputs.length - 1]
    expect(safetyInput.element.value).toBe('')

    await dialog
      .findAll('button')
      .find((b) => b.text().includes('确定'))
      .trigger('click')
    await flushPromises()

    const payload = updateAccessory.mock.calls[0][0]
    expect(payload.safetyStock).toBeNull()

    wrapper.unmount()
  })
})
