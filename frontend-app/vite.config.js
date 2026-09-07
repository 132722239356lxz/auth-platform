import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import path from 'path'

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './')
    }
  },
  server: {
    port: 5174,
    host: '0.0.0.0',
    proxy: {
      '/auth-server': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/system-server': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/auth-flow': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/auth-message': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/ai-agent-server': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/log-server': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
