import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.js'],
    // element-plus 的 ESM 依赖 async-validator 未提供 exports 字段，vitest 将其作为
    // 外部 CJS 加载时 default 互操作会拿到 { default: Schema } 而非构造器，
    // 导致 el-form 的必填校验在测试中静默放行；内联后走 Vite ESM 转换，校验正常 reject
    server: { deps: { inline: [/element-plus/, /async-validator/] } }
  },
  server: {
    host: '127.0.0.1',
    port: 3008,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8088',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '127.0.0.1',
    port: 3008,
    strictPort: true
  }
})
