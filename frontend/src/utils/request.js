import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 读取二进制错误流：优先用 Blob.text()，旧环境回退到 FileReader
const readBlobAsText = (blob) => {
  if (typeof blob.text === 'function') {
    return blob.text()
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}

// blob 请求也可能在 HTTP 200 中返回 JSON 业务错误体（全局异常处理器不改变状态码，
// 如待确认盘点单导出被拒绝）：识别后提示后端原因并拒绝，避免把错误 JSON 当成 CSV 下载；
// 非 JSON 或解析失败时按正常文件透传
const handleJsonBlobResponse = async (response) => {
  try {
    const res = JSON.parse(await readBlobAsText(response.data))
    if (res && res.code != null && res.code !== 200) {
      const message = res.message || '导出失败'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
  } catch (e) {
    // 非 JSON 内容按正常文件处理
  }
  return response
}

request.interceptors.response.use(
  response => {
    // 文件下载等二进制响应直接返回原始 response，由调用方处理 Blob；
    // 但若体是 JSON（HTTP 200 中的业务错误，code!=200），解析后按错误处理
    if (response.config?.responseType === 'blob') {
      const blob = response.data
      if (blob && typeof blob.type === 'string' && blob.type.includes('application/json')) {
        return handleJsonBlobResponse(response)
      }
      return response
    }
    const res = response.data
    if (res.code === 200) {
      return res.data
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  async error => {
    // blob 模式下后端业务错误（如筛选异常）也以 Blob 返回，解析出 JSON 提示后再展示
    const errorData = error.response?.data
    if (error.response?.config?.responseType === 'blob'
      && errorData && typeof errorData.type === 'string'
      && errorData.type.includes('application/json')) {
      try {
        const res = JSON.parse(await readBlobAsText(errorData))
        ElMessage.error(res.message || '导出失败')
        return Promise.reject(new Error(res.message || '导出失败'))
      } catch (e) {
        // 解析失败时走通用错误提示
      }
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
