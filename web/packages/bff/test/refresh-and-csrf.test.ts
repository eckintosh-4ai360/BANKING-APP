import { describe, expect, it, vi } from 'vitest';
import { verifyCsrf } from '../src/csrf';
import { REFRESH_MARGIN_MS, ROTATION_GRACE_MS, TokenRefresher } from '../src/refresh';
import { browserRequest, envelope, errorEnvelope, ORIGIN, session, testConfig, tokens } from './support';

describe('TokenRefresher', () => {
  it('uses the current token while it is comfortably valid', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const result = await new TokenRefresher(testConfig()).ensureFresh(session());
    expect(result).toMatchObject({ status: 'valid', rotated: false });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refreshes a token about to expire and sends only the refresh token', async () => {
    const now = Date.now();
    const fetchMock = vi.fn(async () => envelope(tokens({ accessToken: 'access-2', refreshToken: 's.tenant.refresh-2' }, now)));
    vi.stubGlobal('fetch', fetchMock);
    const result = await new TokenRefresher(testConfig(), () => now).ensureFresh(
      session({ accessTokenExpiresAt: now + REFRESH_MARGIN_MS - 1 }, now),
    );
    expect(result.status).toBe('valid');
    expect(result.status === 'valid' && result.session.accessToken).toBe('access-2');
    expect(result.status === 'valid' && result.rotated).toBe(true);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://backend.test/api/v1/auth/token/refresh');
    expect(JSON.parse(init.body as string)).toEqual({ refreshToken: 's.tenant.refresh-1' });
  });

  it('shares one backend call between concurrent refreshes of the same token (no reuse detection)', async () => {
    const now = Date.now();
    let resolve: (response: Response) => void = () => undefined;
    const fetchMock = vi.fn(() => new Promise<Response>((done) => (resolve = done)));
    vi.stubGlobal('fetch', fetchMock);
    const refresher = new TokenRefresher(testConfig(), () => now);
    const expiring = session({ accessTokenExpiresAt: now }, now);

    const results = Promise.all([refresher.ensureFresh(expiring), refresher.ensureFresh(expiring), refresher.ensureFresh(expiring)]);
    await Promise.resolve();
    resolve(envelope(tokens({ accessToken: 'access-2' }, now)));
    const settled = await results;

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(settled.every((result) => result.status === 'valid' && result.session.accessToken === 'access-2')).toBe(true);
  });

  it('hands the rotated session to late requests carrying the old cookie, within the grace period only', async () => {
    let now = Date.now();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(envelope(tokens({ accessToken: 'access-2' }, now)))
      .mockResolvedValueOnce(errorEnvelope(401, 'INVALID_REFRESH_TOKEN', 'Invalid'));
    vi.stubGlobal('fetch', fetchMock);
    const refresher = new TokenRefresher(testConfig(), () => now);
    const expiring = session({ accessTokenExpiresAt: now }, now);

    await refresher.ensureFresh(expiring);
    const late = await refresher.ensureFresh(expiring);
    expect(late.status === 'valid' && late.session.accessToken).toBe('access-2');
    expect(fetchMock).toHaveBeenCalledTimes(1);

    now += ROTATION_GRACE_MS + 1;
    const tooLate = await refresher.ensureFresh({ ...expiring, accessTokenExpiresAt: now });
    expect(tooLate.status).toBe('expired');
  });

  it('reports an expired session when the backend rejects the refresh token', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => errorEnvelope(401, 'INVALID_REFRESH_TOKEN', 'Invalid')));
    const now = Date.now();
    const result = await new TokenRefresher(testConfig(), () => now).ensureFresh(session({ accessTokenExpiresAt: now }, now));
    expect(result.status).toBe('expired');
  });

  it('does not call the backend once the refresh token itself has expired', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const now = Date.now();
    const result = await new TokenRefresher(testConfig(), () => now).ensureFresh(
      session({ accessTokenExpiresAt: now - 1, refreshTokenExpiresAt: now - 1 }, now),
    );
    expect(result.status).toBe('expired');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('throws (keeping the session) when the backend is down rather than signing the user out', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('down', { status: 503 })));
    const now = Date.now();
    await expect(new TokenRefresher(testConfig(), () => now).ensureFresh(session({ accessTokenExpiresAt: now }, now))).rejects.toThrow();
  });
});

describe('verifyCsrf', () => {
  const config = { allowedOrigins: [ORIGIN] };

  it('accepts same-origin requests from the app', () => {
    expect(verifyCsrf(browserRequest('/api/bff/customers', { method: 'POST', body: {} }), config)).toEqual({ ok: true });
  });

  it('rejects requests without the BFF header (plain form posts, image tags, links)', () => {
    const request = browserRequest('/api/bff/customers', { method: 'POST', body: {}, headers: { 'x-requested-with': '' } });
    expect(verifyCsrf(request, config).ok).toBe(false);
  });

  it('rejects state changes from another origin', () => {
    const request = browserRequest('/api/bff/customers', { method: 'POST', body: {}, headers: { origin: 'https://evil.test' } });
    expect(verifyCsrf(request, config).ok).toBe(false);
  });

  it('rejects state changes without an Origin header', () => {
    const request = new Request(`${ORIGIN}/api/bff/customers`, { method: 'POST', headers: { 'x-requested-with': 'banking-bff' } });
    expect(verifyCsrf(request, config).ok).toBe(false);
  });

  it('rejects cross-site and same-site fetches reported by the browser', () => {
    for (const site of ['cross-site', 'same-site', 'none']) {
      const request = browserRequest('/api/bff/me', { headers: { 'sec-fetch-site': site } });
      expect(verifyCsrf(request, config).ok).toBe(false);
    }
  });

  it('falls back to the request origin when no allow-list is configured (development)', () => {
    const request = new Request('http://localhost:3000/api/bff/branches', {
      method: 'POST',
      headers: { 'x-requested-with': 'banking-bff', origin: 'http://localhost:3000', host: 'localhost:3000' },
    });
    expect(verifyCsrf(request, { allowedOrigins: [] }).ok).toBe(true);
    const foreign = new Request('http://localhost:3000/api/bff/branches', {
      method: 'POST',
      headers: { 'x-requested-with': 'banking-bff', origin: 'http://localhost:3001', host: 'localhost:3000' },
    });
    expect(verifyCsrf(foreign, { allowedOrigins: [] }).ok).toBe(false);
  });
});
