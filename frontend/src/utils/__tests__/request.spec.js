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

  it('blob 响应直接透传完整 response 供下载使用', () => {
    const response = {
      config: { responseType: 'blob' },
      headers: { 'content-disposition': 'attachment' },
      data: new Blob(['csv'], { type: 'text/csv' })
    }
    const result = handlers.onFulfilled(response)
    expect(result).toBe(response)
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('blob 请求在 HTTP 200 中返回 JSON 业务错误体时解析后端提示并拒绝', async () => {
    // 全局异常处理器不改变 HTTP 状态码：待确认单导出被拒绝时为 200 + {code:500,...}
    const response = {
      config: { responseType: 'blob' },
      headers: { 'content-type': 'application/json' },
      data: new Blob([JSON.stringify({ code: 500, message: '待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细' })], {
        type: 'application/json'
      })
    }
    await expect(handlers.onFulfilled(response)).rejects.toThrow('待确认盘点单尚未回写库存')
    expect(ElMessage.error).toHaveBeenCalledWith('待确认盘点单尚未回写库存，差异未定稿，请确认并回写后再导出差异明细')
  })

  it('blob 请求返回 JSON 错误体时解析后端提示', async () => {
    const error = {
      message: 'Request failed with status code 500',
      response: {
        config: { responseType: 'blob' },
        data: new Blob([JSON.stringify({ code: 500, message: '导出失败，请重试' })], {
          type: 'application/json'
        })
      }
    }
    await expect(handlers.onRejected(error)).rejects.toThrow('导出失败，请重试')
    expect(ElMessage.error).toHaveBeenCalledWith('导出失败，请重试')
  })

  it('blob 请求返回非 JSON 错误流时走通用错误提示', async () => {
    const error = {
      message: 'Network Error',
      response: {
        config: { responseType: 'blob' },
        data: new Blob(['<html>502 Bad Gateway</html>'], { type: 'text/html' })
      }
    }
    await expect(handlers.onRejected(error)).rejects.toThrow('Network Error')
    expect(ElMessage.error).toHaveBeenCalledWith('Network Error')
  })
})
