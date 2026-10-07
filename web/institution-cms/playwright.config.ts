import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke tests against a running stack: banking-core with the local profile (demo data) and this app.
 * Start them first (see docs/deployment/local-development.md), then: npm run e2e --workspace institution-cms
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
