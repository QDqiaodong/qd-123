import { describe, it, expect, vi, beforeEach } from 'vitest'

// 捕获 axios 实例上注册的响应拦截器
const { handlers } = vi.hoisted(() => ({ handlers: {} }))

vi.mock('axios', () => ({
  default: {
    create: () => ({
      interceptors: {
        response: {
          use: (onFulfilled, onRejected) => {
            handlers.onFulfilled = onFulfilled
            handlers.onRejected = onRejected
          }
        }
      }
    })
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn()
  }
}))

const { ElMessage } = await import('element-plus')
await import('@/utils/request')

describe('request 响应拦截器', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('code 为 200 时直接返回业务数据', () => {
    const payload = { records: [], total: 0 }
    const result = handlers.onFulfilled({ data: { code: 200, message: 'success', data: payload } })
    expect(result).toBe(payload)
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('业务失败时展示后端提示并拒绝 Promise', async () => {
    const response = { data: { code: 500, message: '布线方案不存在或已被删除' } }
    await expect(handlers.onFulfilled(response)).rejects.toThrow('布线方案不存在或已被删除')
    expect(ElMessage.error).toHaveBeenCalledWith('布线方案不存在或已被删除')
  })

  it('后端未返回提示时展示默认错误文案', async () => {
    const response = { data: { code: 500 } }
    await expect(handlers.onFulfilled(response)).rejects.toThrow('请求失败')
    expect(ElMessage.error).toHaveBeenCalledWith('请求失败')
  })

  it('网络异常时展示错误并拒绝 Promise', async () => {
    const error = new Error('Network Error')
    await expect(handlers.onRejected(error)).rejects.toThrow('Network Error')
    expect(ElMessage.error).toHaveBeenCalledWith('Network Error')
  })
})
