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

request.interceptors.response.use(
  response => {
    // 文件下载等二进制响应直接返回原始 response，由调用方处理 Blob
    if (response.config?.responseType === 'blob') {
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
