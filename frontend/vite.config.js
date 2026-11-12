import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 端口取自根目录 .env 的 WEB_PORT，与 scripts/dev.sh 传的 --port 保持一致
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd() + '/..', '')
  const port = Number(env.WEB_PORT || 8089)

  return {
    plugins: [vue()],
    server: {
      port,
      host: true,
      proxy: {
        // 开发态走代理，浏览器同源 -> 不需要后端开 CORS
        '/api': {
          target: 'http://127.0.0.1:8080',
          changeOrigin: true
        }
      }
    }
  }
})
