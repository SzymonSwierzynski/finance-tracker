import { configDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath } from 'node:url'

// In dev we proxy /api -> Spring Boot so the SPA is same-origin (first-party cookies,
// no CORS). In prod, nginx serves the bundle and proxies /api to the backend.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    css: false,
    // Playwright specs live in e2e/ and match the *.spec.ts glob — keep Vitest out of them.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
})
