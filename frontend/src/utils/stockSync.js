// 现存量变更通知：配件档案保存（新增/编辑）现存量、分区调整、方案核销出库后，
// 布线方案列表的库存校验与库存缺口分析都依赖同一现存量实时重算，
// 仅刷新当前页会让其他已打开页面停留在旧的“充足/不足”与缺口数字上，故统一在此广播，
// 各页面监听到后立即按最新现存量重新拉取。
// 同时触发 window 的 stock-changed 事件，覆盖页面被浏览器 bfcache 恢复（pageshow）后
// 组件内存状态仍旧、且不会重新 onMounted 的场景。
const listeners = new Set()

// 广播库存相关数据已变化。source 为发起变更的页面标识，本页一般已自行刷新，
// 通过 source 跳过可避免来源页重复拉取
export const notifyStockChanged = (source) => {
  listeners.forEach((listener) => {
    try {
      listener(source)
    } catch (e) {
      // 单个页面刷新失败不影响其他页面
    }
  })
  try {
    window.dispatchEvent(new CustomEvent('stock-changed', { detail: { source } }))
  } catch (e) {
    // 非浏览器环境（如单测 jsdom 异常）忽略
  }
}

// 订阅库存变更；listener 收到发起方 source，可据此跳过自身发起的通知。
// 返回退订函数，组件卸载时调用
export const onStockChanged = (listener) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

// 仅供单测在用例间清空订阅，避免前序用例未卸载的组件残留监听
export const __resetStockListenersForTests = () => {
  listeners.clear()
}
