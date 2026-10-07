import { loadBffConfig, type BffConfig } from '../src/config';
import type { BackendTokenResponse, SessionData } from '../src/session';

export const ORIGIN = 'https://cms.bank.test';

export function testConfig(overrides: Partial<BffConfig> = {}): BffConfig {
  return {
    ...loadBffConfig('cms', 'staff', {
      NODE_ENV: 'test',
      BACKEND_URL: 'http://backend.test',
      SESSION_SECRET: 'test-secret-that-is-long-enough-0123456789',
      APP_ORIGIN: ORIGIN,
      COOKIE_SECURE: 'true',
    }),
    ...overrides,
  };
}

export function browserRequest(
  path: string,
  init: { method?: string; body?: unknown; cookie?: string; headers?: Record<string, string> } = {},
): Request {
  const headers = new Headers({ 'x-requested-with': 'banking-bff', origin: ORIGIN, 'sec-fetch-site': 'same-origin' });
  if (init.cookie) {
    headers.set('cookie', init.cookie);
  }
  if (init.body !== undefined) {
    headers.set('content-type', 'application/json');
  }
  for (const [name, value] of Object.entries(init.headers ?? {})) {
    headers.set(name, value);
  }
  return new Request(`${ORIGIN}${path}`, {
    method: init.method ?? (init.body === undefined ? 'GET' : 'POST'),
    headers,
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
  });
}

/** Turns Set-Cookie headers into the Cookie header a browser would send next. */
export function cookieHeaderFrom(response: Response, previous = ''): string {
  const jar = new Map<string, string>();
  for (const part of previous.split(';')) {
    const index = part.indexOf('=');
    if (index > 0) {
      jar.set(part.slice(0, index).trim(), part.slice(index + 1).trim());
    }
  }
  for (const header of response.headers.getSetCookie()) {
    const [pair = '', ...attributes] = header.split(';').map((piece) => piece.trim());
    const index = pair.indexOf('=');
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (attributes.some((attribute) => attribute === 'Max-Age=0')) {
      jar.delete(name);
    } else {
      jar.set(name, value);
    }
  }
  return Array.from(jar, ([name, value]) => `${name}=${value}`).join('; ');
}

export function tokens(overrides: Partial<BackendTokenResponse> = {}, now = Date.now()): BackendTokenResponse {
  return {
    tokenType: 'Bearer',
    accessToken: `access-${Math.random().toString(36).slice(2)}`,
    accessTokenExpiresAt: new Date(now + 10 * 60_000).toISOString(),
    refreshToken: `s.tenant.${Math.random().toString(36).slice(2)}`,
    refreshTokenExpiresAt: new Date(now + 8 * 60 * 60_000).toISOString(),
    passwordChangeRequired: false,
    mfaEnrollmentRequired: false,
    mfaRequired: false,
    mfaChallengeToken: null,
    mfaChallengeExpiresAt: null,
    ...overrides,
  };
}

export function session(overrides: Partial<SessionData> = {}, now = Date.now()): SessionData {
  return {
    kind: 'staff',
    accessToken: 'access-1',
    accessTokenExpiresAt: now + 10 * 60_000,
    refreshToken: 's.tenant.refresh-1',
    refreshTokenExpiresAt: now + 8 * 60 * 60_000,
    passwordChangeRequired: false,
    mfaEnrollmentRequired: false,
    createdAt: now,
    ...overrides,
  };
}

export function envelope(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({ success: true, message: 'OK', data, timestamp: new Date().toISOString() }), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

export function errorEnvelope(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ success: false, code, message, timestamp: new Date().toISOString() }), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
