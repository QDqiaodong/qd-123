// CSV 导出下载辅助：解析后端中文文件名、触发浏览器下载，方案导出与缺口分区汇总导出共用

// 导出文件名主体最长保留 80 个字符（含扩展名），防止超长名称拼出的文件名超出文件系统限制
export const MAX_FILENAME_LENGTH = 80

// 超长时截断文件名主体并保留扩展名
export const truncateFileName = (name, maxLength = MAX_FILENAME_LENGTH) => {
  if (!name || name.length <= maxLength) {
    return name
  }
  const dotIndex = name.lastIndexOf('.')
  const ext = dotIndex >= 0 ? name.slice(dotIndex) : ''
  const stem = dotIndex >= 0 ? name.slice(0, dotIndex) : name
  return stem.slice(0, maxLength - ext.length) + ext
}

// 优先使用后端返回的中文文件名（Content-Disposition: filename*），解析失败或超长时使用兜底名
export const resolveExportFileName = (contentDisposition, fallbackFileName) => {
  if (!contentDisposition) {
    return fallbackFileName
  }
  const starMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (starMatch && starMatch[1]) {
    try {
      const name = decodeURIComponent(starMatch[1])
      return truncateFileName(name) || fallbackFileName
    } catch (e) {
      return fallbackFileName
    }
  }
  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  if (plainMatch && plainMatch[1] && /[一-龥]/.test(plainMatch[1])) {
    return truncateFileName(plainMatch[1])
  }
  return fallbackFileName
}

export const triggerBrowserDownload = (blob, fileName) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 释放 Blob URL，避免内存泄漏
  window.URL.revokeObjectURL(url)
}
