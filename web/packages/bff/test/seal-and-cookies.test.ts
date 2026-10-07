import { describe, expect, it } from 'vitest';
import { loadBffConfig } from '../src/config';
import { CHUNK_SIZE, clearChunked, parseCookies, readChunked, writeChunked } from '../src/cookies';
import { seal, unseal } from '../src/seal';
import { SessionStore } from '../src/session';
import { browserRequest, cookieHeaderFrom, session, testConfig } from './support';

const SECRETS = ['first-secret-that-is-long-enough-0123456789'];

describe('seal / unseal', () => {
  it('round-trips data for the same purpose', () => {
    const sealed = seal({ token: 'abc' }, 'cms:session', Date.now() + 60_000, SECRETS);
    expect(sealed.startsWith('v1.')).toBe(true);
    expect(sealed).not.toContain('abc');
    expect(unseal(sealed, 'cms:session', SECRETS)).toEqual({ token: 'abc' });
  });

  it('rejects a value sealed for another purpose', () => {
    const sealed = seal({ challengeToken: 'x' }, 'cms:mfa', Date.now() + 60_000, SECRETS);
    expect(unseal(sealed, 'cms:session', SECRETS)).toBeNull();
    expect(unseal(sealed, 'admin:mfa', SECRETS)).toBeNull();
  });

  it('rejects tampered values', () => {
    const sealed = seal({ admin: false }, 'cms:session', Date.now() + 60_000, SECRETS);
    const parts = sealed.split('.');
    const payload = Buffer.from(parts[3]!, 'base64url');
    payload[0] = payload[0]! ^ 0x01;
    parts[3] = payload.toString('base64url');
    expect(unseal(parts.join('.'), 'cms:session', SECRETS)).toBeNull();
    expect(unseal('v1.garbage', 'cms:session', SECRETS)).toBeNull();
    expect(unseal('', 'cms:session', SECRETS)).toBeNull();
  });

  it('enforces the embedded expiry even if the browser keeps the cookie', () => {
    const now = Date.now();
    const sealed = seal({ ok: true }, 'p', now + 1_000, SECRETS);
    expect(unseal(sealed, 'p', SECRETS, now)).toEqual({ ok: true });
    expect(unseal(sealed, 'p', SECRETS, now + 1_000)).toBeNull();
  });

  it('opens values sealed with a previous secret during rotation, and not with an unknown one', () => {
    const old = seal({ v: 1 }, 'p', Date.now() + 60_000, SECRETS);
    const rotated = ['second-secret-that-is-long-enough-0123456789', ...SECRETS];
    expect(unseal(old, 'p', rotated)).toEqual({ v: 1 });
    expect(unseal(old, 'p', ['some-other-secret-that-is-long-enough-012345'])).toBeNull();
  });
});

describe('chunked cookies', () => {
  it('splits large values and reassembles them', () => {
    const value = 'x'.repeat(CHUNK_SIZE * 2 + 10);
    const headers = writeChunked('s', value, { secure: true, maxAgeSeconds: 60 }, new Map());
    expect(headers).toHaveLength(4);
    for (const header of headers) {
      expect(header).toContain('HttpOnly');
      expect(header).toContain('SameSite=Strict');
      expect(header).toContain('Secure');
      expect(header).toContain('Path=/');
    }
    const cookies = new Map(headers.map((header) => header.split(';')[0]!.split('=') as [string, string]));
    expect(readChunked(cookies, 's')).toBe(value);
  });

  it('expires chunks left over from a longer previous value', () => {
    const existing = new Map([
      ['s.0', 'a'],
      ['s.1', 'b'],
      ['s.2', 'c'],
      ['s.n', '3'],
    ]);
    const headers = writeChunked('s', 'short', { secure: false, maxAgeSeconds: 60 }, existing);
    expect(headers.some((header) => header.startsWith('s.1=;') && header.includes('Max-Age=0'))).toBe(true);
    expect(headers.some((header) => header.startsWith('s.2=;') && header.includes('Max-Age=0'))).toBe(true);
  });

  it('treats a missing chunk as no value and clears every chunk', () => {
    const cookies = new Map([
      ['s.n', '2'],
      ['s.0', 'a'],
      ['other', 'keep'],
    ]);
    expect(readChunked(cookies, 's')).toBeUndefined();
    const cleared = clearChunked('s', { secure: false }, cookies);
    expect(cleared).toHaveLength(2);
    expect(cleared.every((header) => header.includes('Max-Age=0'))).toBe(true);
  });

  it('parses cookie headers', () => {
    const request = new Request('https://x.test/', { headers: { cookie: 'a=1; b=two=2;  c=3' } });
    const cookies = parseCookies(request);
    expect(cookies.get('a')).toBe('1');
    expect(cookies.get('b')).toBe('two=2');
    expect(cookies.get('c')).toBe('3');
  });
});

describe('SessionStore', () => {
  it('stores sessions under __Host- cookies that only this app can open', () => {
    const cms = new SessionStore(testConfig());
    const response = new Response(null, { headers: cms.write(browserRequest('/'), session()).map((cookie) => ['set-cookie', cookie] as [string, string]) });
    const cookie = cookieHeaderFrom(response);
    expect(cookie).toContain('__Host-cms_session.0=');
    expect(cms.read(browserRequest('/', { cookie }))?.accessToken).toBe('access-1');

    const admin = new SessionStore({ ...testConfig(), appId: 'admin', kind: 'platform' });
    const renamed = cookie.replaceAll('__Host-cms_session', '__Host-admin_session');
    expect(admin.read(browserRequest('/', { cookie: renamed }))).toBeNull();
  });

  it('caps the cookie lifetime at the absolute session limit', () => {
    const config = testConfig({ sessionMaxAgeSeconds: 3600 });
    const now = Date.now();
    const headers = new SessionStore(config).write(browserRequest('/'), session({}, now), now);
    expect(headers.every((header) => header.includes('Max-Age=3600'))).toBe(true);
  });
});

describe('loadBffConfig', () => {
  const production = {
    NODE_ENV: 'production',
    BACKEND_URL: 'http://banking-core:8080',
    SESSION_SECRET: 'a-production-secret-that-is-long-enough-123',
    APP_ORIGIN: 'https://cms.bank.example',
  };

  it('accepts a complete production configuration', () => {
    const config = loadBffConfig('cms', 'staff', production);
    expect(config.secureCookies).toBe(true);
    expect(config.allowedOrigins).toEqual(['https://cms.bank.example']);
  });

  it.each([
    ['a missing secret', { SESSION_SECRET: undefined }],
    ['a short secret', { SESSION_SECRET: 'short' }],
    ['the development secret', { SESSION_SECRET: 'development-only-session-secret-change-me-0123456789' }],
    ['insecure cookies', { COOKIE_SECURE: 'false' }],
    ['no origin allow-list', { APP_ORIGIN: undefined }],
    ['a plain-http origin', { APP_ORIGIN: 'http://cms.bank.example' }],
    ['a missing backend URL', { BACKEND_URL: undefined }],
  ])('refuses %s in production', (_, override) => {
    expect(() => loadBffConfig('cms', 'staff', { ...production, ...override })).toThrow();
  });

  it('has working development defaults', () => {
    const config = loadBffConfig('admin', 'platform', { NODE_ENV: 'development' });
    expect(config.backendUrl).toBe('http://localhost:8080');
    expect(config.secureCookies).toBe(false);
  });
});
