import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 将大体积依赖拆成独立 chunk，便于浏览器长期缓存和定位包体问题。
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined;
          }

          if (id.includes('element-plus')) {
            return 'element-plus';
          }

          if (
            id.includes('@tiptap')
            || id.includes('prosemirror')
          ) {
            return 'editor';
          }

          if (id.includes('dompurify')) {
            return 'sanitize';
          }

          if (
            id.includes('/vue/')
            || id.includes('/vue-router/')
            || id.includes('@vue')
          ) {
            return 'vue-vendor';
          }

          return 'vendor';
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8080,
    strictPort: true,
    // 保留 Vite 默认主机校验，避免 allowedHosts: true 带来的 DNS rebinding 风险。
    proxy: {
      '/api': {
        target: 'http://localhost:5090',
        changeOrigin: true,
      },
    },
  },
})
