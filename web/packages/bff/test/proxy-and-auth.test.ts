import { describe, expect, it, vi } from 'vitest';
import { createAuthHandlers } from '../src/auth';
import { createProxy, institutionRule, platformRule } from '../src/proxy';
import { TokenRefresher } from '../src/refresh';
import { SessionStore } from '../src/session';
import { browserRequest, cookieHeaderFrom, envelope, errorEnvelope, session, testConfig, tokens } from './support';

function setup() {
  const config = testConfig();
  const store = new SessionStore(config);
  const refresher = new TokenRefresher(config);
  return {
    config,
    store,
    proxy: createProxy({ config, store, refresher, rule: institutionRule }),
    auth: createAuthHandlers({ config, store, refresher }),
  };
}

function cookieFor(store: SessionStore, data = session()): string {
  const response = new Response(null, { headers: store.write(browserRequest('/'), data).map((cookie) => ['set-cookie', cookie] as [string, string]) });
  return cookieHeaderFrom(response);
}

describe('proxy rules', () => {
  it('keeps each app on its own side of the API', () => {
    expect(institutionRule(['customers'], 'GET')).toBe(true);
    expect(institutionRule(['platform', 'tenants'], 'GET')).toBe(false);
    expect(institutionRule(['auth', 'token', 'refresh'], 'POST')).toBe(false);
    expect(institutionRule([], 'GET')).toBe(false);
    expect(platformRule(['platform', 'tenants'], 'GET')).toBe(true);
    expect(platformRule(['platform', 'auth', 'login'], 'POST')).toBe(false);
    expect(platformRule(['customers'], 'GET')).toBe(false);
  });
});

describe('proxy', () => {
  it('forwards the call with the bearer token and nothing from the browser cookie jar', async () => {
    const { proxy, store } = setup();
    const fetchMock = vi.fn(async () => envelope({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);

    const response = await proxy(browserRequest('/api/bff/customers?q=mensah', { cookie: `${cookieFor(store)}; tracking=1` }), ['customers']);

    expect(response.status).toBe(200);
    expect(response.headers.get('cache-control')).toBe('no-store');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://backend.test/api/v1/customers?q=mensah');
    const headers = new Headers(init.headers);
    expect(headers.get('authorization')).toBe('Bearer access-1');
    expect(headers.get('cookie')).toBeNull();
    expect(headers.get('x-correlation-id')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('answers 401 without calling the backend when there is no session', async () => {
    const { proxy } = setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const response = await proxy(browserRequest('/api/bff/customers'), ['customers']);
    expect(response.status).toBe(401);
    expect(((await response.json()) as { code: string }).code).toBe('UNAUTHENTICATED');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses paths outside the allow-list and traversal attempts', async () => {
    const { proxy, store } = setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const cookie = cookieFor(store);
    for (const segments of [['platform', 'tenants'], ['auth', 'logout'], ['..', 'actuator'], ['customers', '%2e%2e'], ['customers', 'a/b']]) {
      const response = await proxy(browserRequest('/api/bff/x', { cookie }), segments);
      expect(response.status).toBe(404);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses cross-site requests', async () => {
    const { proxy, store } = setup();
    const response = await proxy(
      browserRequest('/api/bff/customers', { method: 'POST', body: {}, cookie: cookieFor(store), headers: { origin: 'https://evil.test' } }),
      ['customers'],
    );
    expect(response.status).toBe(403);
  });

  it('rotates the cookie when it had to refresh the access token', async () => {
    const { proxy, store } = setup();
    const now = Date.now();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(envelope(tokens({ accessToken: 'access-2' }, now)))
      .mockResolvedValueOnce(envelope({ id: 'me' }));
    vi.stubGlobal('fetch', fetchMock);
    const cookie = cookieFor(store, session({ accessTokenExpiresAt: now + 1_000 }, now));

    const response = await proxy(browserRequest('/api/bff/me', { cookie }), ['me']);

    expect(response.status).toBe(200);
    const next = cookieHeaderFrom(response, cookie);
    expect(store.read(browserRequest('/', { cookie: next }))?.accessToken).toBe('access-2');
    const [, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect(new Headers(init.headers).get('authorization')).toBe('Bearer access-2');
  });

  it('drops the cookie when the backend no longer accepts the session', async () => {
    const { proxy, store } = setup();
    vi.stubGlobal('fetch', vi.fn(async () => errorEnvelope(401, 'SESSION_REVOKED', 'Session ended')));
    const cookie = cookieFor(store);
    const response = await proxy(browserRequest('/api/bff/me', { cookie }), ['me']);
    expect(response.status).toBe(401);
    expect(store.read(browserRequest('/', { cookie: cookieHeaderFrom(response, cookie) }))).toBeNull();
  });

  it('passes validation errors through untouched', async () => {
    const { proxy, store } = setup();
    vi.stubGlobal('fetch', vi.fn(async () => errorEnvelope(400, 'VALIDATION_FAILED', 'Invalid request')));
    const response = await proxy(browserRequest('/api/bff/branches', { method: 'POST', body: { code: '' }, cookie: cookieFor(store) }), ['branches']);
    expect(response.status).toBe(400);
    expect(((await response.json()) as { code: string }).code).toBe('VALIDATION_FAILED');
  });

  it('forwards well-formed idempotency keys and rejects malformed ones', async () => {
    const { proxy, store } = setup();
    const fetchMock = vi.fn(async () => envelope({}));
    vi.stubGlobal('fetch', fetchMock);
    const cookie = cookieFor(store);

    const bad = await proxy(browserRequest('/api/bff/x', { method: 'POST', body: {}, cookie, headers: { 'idempotency-key': 'bad key!' } }), ['branches']);
    expect(bad.status).toBe(400);

    await proxy(browserRequest('/api/bff/x', { method: 'POST', body: {}, cookie, headers: { 'idempotency-key': 'b7c1f0d2-5a8e-4c1b-9a43-2f6c0e9d1a77' } }), ['branches']);
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(new Headers(init.headers).get('idempotency-key')).toBe('b7c1f0d2-5a8e-4c1b-9a43-2f6c0e9d1a77');
  });

  it('answers 503 and keeps the session when the backend is unreachable', async () => {
    const { proxy, store } = setup();
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('fetch failed'))));
    const response = await proxy(browserRequest('/api/bff/me', { cookie: cookieFor(store) }), ['me']);
    expect(response.status).toBe(503);
    expect(response.headers.getSetCookie()).toHaveLength(0);
  });
});

describe('auth handlers', () => {
  it('signs in, keeps the tokens in the sealed cookie and returns only flags', async () => {
    const { auth, store } = setup();
    vi.stubGlobal('fetch', vi.fn(async () => envelope(tokens({ accessToken: 'access-9', passwordChangeRequired: true }))));

    const response = await auth.login(browserRequest('/api/auth/login', { body: { tenantCode: 'Demo-MFI', username: 'teller', password: 'pw' } }));

    expect(response.status).toBe(200);
    const text = await response.text();
    expect(text).not.toContain('access-9');
    expect(JSON.parse(text).data).toEqual({ mfaRequired: false, passwordChangeRequired: true, mfaEnrollmentRequired: false });
    const cookie = cookieHeaderFrom(response);
    expect(store.read(browserRequest('/', { cookie }))?.accessToken).toBe('access-9');
  });

  it('sends the normalised tenant code to the staff login endpoint', async () => {
    const { auth } = setup();
    const fetchMock = vi.fn(async () => envelope(tokens()));
    vi.stubGlobal('fetch', fetchMock);
    await auth.login(browserRequest('/api/auth/login', { body: { tenantCode: ' Demo-MFI ', username: 'teller', password: 'pw' } }));
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://backend.test/api/v1/auth/staff/login');
    expect(JSON.parse(init.body as string)).toEqual({ tenantCode: 'demo-mfi', username: 'teller', password: 'pw' });
  });

  it('passes credential errors through without creating a session', async () => {
    const { auth } = setup();
    vi.stubGlobal('fetch', vi.fn(async () => errorEnvelope(401, 'INVALID_CREDENTIALS', 'Invalid credentials')));
    const response = await auth.login(browserRequest('/api/auth/login', { body: { tenantCode: 'demo-mfi', username: 'x', password: 'y' } }));
    expect(response.status).toBe(401);
    expect(((await response.json()) as { code: string }).code).toBe('INVALID_CREDENTIALS');
    expect(response.headers.getSetCookie().every((cookie) => cookie.includes('Max-Age=0'))).toBe(true);
  });

  it('completes an MFA challenge using the sealed challenge cookie', async () => {
    const { auth, store } = setup();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        envelope(
          tokens({
            accessToken: null,
            refreshToken: null,
            accessTokenExpiresAt: null,
            refreshTokenExpiresAt: null,
            mfaRequired: true,
            mfaChallengeToken: 'challenge-1',
            mfaChallengeExpiresAt: new Date(Date.now() + 300_000).toISOString(),
          }),
        ),
      )
      .mockResolvedValueOnce(envelope(tokens({ accessToken: 'access-mfa' })));
    vi.stubGlobal('fetch', fetchMock);

    const first = await auth.login(browserRequest('/api/auth/login', { body: { tenantCode: 'demo-mfi', username: 'admin', password: 'pw' } }));
    expect(((await first.json()) as { data: { mfaRequired: boolean } }).data.mfaRequired).toBe(true);
    const cookie = cookieHeaderFrom(first);
    expect(store.read(browserRequest('/', { cookie }))).toBeNull();

    const second = await auth.verifyMfa(browserRequest('/api/auth/mfa', { body: { code: '123456' }, cookie }));
    expect(second.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect(url).toBe('http://backend.test/api/v1/auth/mfa/verify');
    expect(JSON.parse(init.body as string)).toEqual({ challengeToken: 'challenge-1', code: '123456' });
    const signedIn = cookieHeaderFrom(second, cookie);
    expect(store.read(browserRequest('/', { cookie: signedIn }))?.accessToken).toBe('access-mfa');
    expect(signedIn).not.toContain('cms_mfa');
  });

  it('rejects MFA verification without a pending challenge or with a malformed code', async () => {
    const { auth } = setup();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const response = await auth.verifyMfa(browserRequest('/api/auth/mfa', { body: { code: '123456' } }));
    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('signs out: revokes the backend session and clears the cookie even if the backend fails', async () => {
    const { auth, store } = setup();
    const fetchMock = vi.fn(async () => Promise.reject(new TypeError('down')));
    vi.stubGlobal('fetch', fetchMock);
    const cookie = cookieFor(store);
    const response = await auth.logout(browserRequest('/api/auth/logout', { method: 'POST', cookie }));
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(store.read(browserRequest('/', { cookie: cookieHeaderFrom(response, cookie) }))).toBeNull();
  });

  it('replaces the session after a password change', async () => {
    const { auth, store } = setup();
    vi.stubGlobal('fetch', vi.fn(async () => envelope(tokens({ accessToken: 'access-new' }))));
    const cookie = cookieFor(store, session({ passwordChangeRequired: true }));
    const response = await auth.changePassword(
      browserRequest('/api/auth/password', { body: { currentPassword: 'old', newPassword: 'New-Password-2026!' }, cookie }),
    );
    expect(response.status).toBe(200);
    const updated = store.read(browserRequest('/', { cookie: cookieHeaderFrom(response, cookie) }));
    expect(updated?.accessToken).toBe('access-new');
    expect(updated?.passwordChangeRequired).toBe(false);
  });

  it('describes the session without revealing tokens', async () => {
    const { auth, store } = setup();
    const response = await auth.session(browserRequest('/api/auth/session', { cookie: cookieFor(store) }));
    const body = (await response.json()) as { data: Record<string, unknown> };
    expect(body.data).toEqual({ authenticated: true, kind: 'staff', passwordChangeRequired: false, mfaEnrollmentRequired: false });
  });

  it('refuses auth calls without the CSRF header', async () => {
    const { auth } = setup();
    const response = await auth.login(
      browserRequest('/api/auth/login', { body: { tenantCode: 'demo-mfi', username: 'x', password: 'y' }, headers: { 'x-requested-with': 'XMLHttpRequest' } }),
    );
    expect(response.status).toBe(403);
  });
});
