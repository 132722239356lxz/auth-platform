import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        '/auth-server': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/system-server': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/auth-flow': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/auth-message': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/ai-agent-server': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        },
        '/log-server': {
          target: env.VITE_GATEWAY_URL || 'http://127.0.0.1:8080',
          changeOrigin: true
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`
        }
      }
    }
  }
})
