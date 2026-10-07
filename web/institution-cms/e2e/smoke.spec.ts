import { expect, test, type Page } from '@playwright/test';

/**
 * Smoke flows on the local demo tenant (seeded by banking-core's local profile). They exercise the real BFF and
 * backend: sign-in, permission-gated navigation, customer search and the four-eyes rule on KYC.
 */

const TENANT = process.env.E2E_TENANT ?? 'demo-mfi';
const PASSWORD = process.env.E2E_PASSWORD ?? 'Demo@Pass2026';

async function signIn(page: Page, username: string) {
  await page.goto('/login');
  await page.getByLabel(/Institution code/).fill(TENANT);
  await page.getByLabel(/Username/).fill(username);
  await page.getByLabel(/^Password/).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('redirects anonymous visitors to the login page', async ({ page }) => {
  await page.goto('/customers');
  await expect(page).toHaveURL(/\/login/);
});

test('rejects wrong credentials without revealing which part was wrong', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel(/Institution code/).fill(TENANT);
  await page.getByLabel(/Username/).fill('teller');
  await page.getByLabel(/^Password/).fill('definitely-wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test('a teller sees customers but not administration', async ({ page }) => {
  await signIn(page, 'teller');
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
  const nav = page.getByRole('navigation', { name: 'Main' });
  await expect(nav.getByRole('link', { name: 'Customers' })).toBeVisible();
  await expect(nav.getByRole('link', { name: 'Roles & permissions' })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: 'Audit log' })).toHaveCount(0);
});

test('a manager can search the demo customers', async ({ page }) => {
  await signIn(page, 'manager');
  await page.getByRole('link', { name: 'Customers' }).first().click();
  await page.getByLabel('Search').fill('Mensah');
  await page.getByRole('button', { name: 'Search' }).click();
  await expect(page.getByRole('table', { name: 'Customers' })).toContainText(/Mensah/);
});

test('session cookies are HttpOnly, SameSite=Strict and hold no readable token', async ({ page, context }) => {
  await signIn(page, 'teller');
  await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible();
  const cookies = await context.cookies();
  const session = cookies.filter((cookie) => cookie.name.includes('cms_session'));
  expect(session.length).toBeGreaterThan(0);
  for (const cookie of session) {
    expect(cookie.httpOnly).toBe(true);
    expect(cookie.sameSite).toBe('Strict');
    expect(cookie.value).not.toMatch(/^eyJ/);
  }
  const visibleToScript = await page.evaluate(() => document.cookie);
  expect(visibleToScript).not.toContain('cms_session');
});

test('pages carry a nonce-based Content-Security-Policy', async ({ page }) => {
  const response = await page.goto('/login');
  const policy = response?.headers()['content-security-policy'] ?? '';
  expect(policy).toMatch(/script-src 'self' 'nonce-[^']+' 'strict-dynamic'/);
  expect(policy).toContain("frame-ancestors 'none'");
});

test('the BFF refuses cross-site requests', async ({ request }) => {
  const response = await request.post('/api/bff/customers', {
    headers: { origin: 'https://evil.example', 'x-requested-with': 'banking-bff', 'content-type': 'application/json' },
    data: {},
  });
  expect(response.status()).toBe(403);
});
