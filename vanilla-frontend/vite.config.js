import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 本地开发：/vanilla 请求通过 Vite 代理转发到后端，避免跨域
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 监听 0.0.0.0，允许局域网/容器内访问（仅开发环境）
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/vanilla': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'vue-vendor': ['vue', 'vue-router', 'pinia', 'axios']
        }
      }
    }
  }
})
