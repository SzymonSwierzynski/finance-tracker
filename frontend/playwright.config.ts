import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against the real stack. The Vite dev server (started via `webServer` below)
 * proxies /api -> Spring Boot on :8080, so the SPA is same-origin (first-party refresh cookie).
 *
 * Prerequisite: a running backend + Postgres on :8080. Locally:
 *   docker compose up -d db && (cd backend && ./gradlew bootRun)
 * then `npm run e2e`. The dev server is reused if already running.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
