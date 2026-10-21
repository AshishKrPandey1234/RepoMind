import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const apiTarget = process.env.REPOMIND_API_ORIGIN || 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true, xfwd: true },
      '/oauth2': { target: apiTarget, changeOrigin: true, xfwd: true },
      '/login/oauth2': { target: apiTarget, changeOrigin: true, xfwd: true },
    },
  },
})
