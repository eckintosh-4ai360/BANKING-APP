import type { SignInResult } from '@banking/api';
import { BackendUnavailableError, callBackend, readEnvelope } from './backend';
import type { BffConfig } from './config';
import { verifyCsrf } from './csrf';
import { jsonError, jsonOk } from './http';
import type { TokenRefresher } from './refresh';
import { sessionFromTokens, type BackendTokenResponse, type SessionData, type SessionStore } from './session';

export interface AuthDependencies {
  config: BffConfig;
  store: SessionStore;
  refresher: TokenRefresher;
}

const MAX_AUTH_BODY_BYTES = 8 * 1024;
const MFA_CODE = /^\d{6}$/;
const TENANT_CODE = /^[a-z0-9][a-z0-9-]{1,31}$/;

type Handler = (request: Request) => Promise<Response>;

export interface AuthHandlers {
  /** POST {tenantCode?, username, password}: exchanges credentials for a session or an MFA challenge. */
  login: Handler;
  /** POST {code}: completes the MFA challenge started by login. */
  verifyMfa: Handler;
  /** POST: revokes the backend session and clears the cookies. */
  logout: Handler;
  /** GET: what the browser may know about its session (never the tokens). */
  session: Handler;
  /** POST {currentPassword, newPassword}. */
  changePassword: Handler;
  /** POST: starts TOTP enrolment and returns the secret to show as a QR code. */
  mfaSetup: Handler;
  /** POST {code}: confirms TOTP enrolment. */
  mfaActivate: Handler;
}

/**
 * Credential exchanges. Tokens are kept in the sealed cookie; responses to the browser carry only flags. Errors from
 * banking-core (invalid credentials, lockout, rate limiting, validation) are passed through unchanged so the backend
 * stays the single source of those rules and messages.
 */
export function createAuthHandlers({ config, store, refresher }: AuthDependencies): AuthHandlers {
  const loginPath = config.kind === 'staff' ? '/api/v1/auth/staff/login' : '/api/v1/platform/auth/login';

  async function guarded(request: Request, method: 'GET' | 'POST', action: () => Promise<Response>): Promise<Response> {
    if (request.method.toUpperCase() !== method) {
      return jsonError(405, 'METHOD_NOT_ALLOWED', 'Method not allowed');
    }
    if (!verifyCsrf(request, config).ok) {
      return jsonError(403, 'REQUEST_REJECTED', 'The request was rejected');
    }
    try {
      return await action();
    } catch (error) {
      console.error(`[bff] auth ${new URL(request.url).pathname} failed: ${error instanceof BackendUnavailableError ? 'backend unavailable' : 'unexpected error'}`);
      return jsonError(503, 'SERVICE_UNAVAILABLE', 'The service is temporarily unavailable. Please try again.');
    }
  }

  /** Runs an action with a fresh session; answers 401 (and clears cookies) when there is none. */
  async function withSession(request: Request, action: (session: SessionData, setCookies: string[]) => Promise<Response>): Promise<Response> {
    const session = store.read(request);
    if (!session) {
      return unauthenticated(request);
    }
    const fresh = await refresher.ensureFresh(session, request);
    if (fresh.status === 'expired') {
      return unauthenticated(request);
    }
    return action(fresh.session, fresh.rotated ? store.write(request, fresh.session) : []);
  }

  function unauthenticated(request: Request): Response {
    return jsonError(401, 'UNAUTHENTICATED', 'Your session has ended. Please sign in again.', {
      setCookies: [...store.clear(request), ...store.clearMfa(request)],
    });
  }

  /** Stores the tokens of a successful exchange and tells the browser only what it needs to route the user. */
  function establish(request: Request, tokens: BackendTokenResponse, createdAt?: number): Response {
    const session = sessionFromTokens(config.kind, tokens, Date.now(), createdAt);
    if (!session) {
      return jsonError(502, 'BAD_GATEWAY', 'Unexpected response from the authentication service');
    }
    const result: SignInResult = {
      mfaRequired: false,
      passwordChangeRequired: session.passwordChangeRequired,
      mfaEnrollmentRequired: session.mfaEnrollmentRequired,
    };
    return jsonOk(result, { setCookies: [...store.write(request, session), ...store.clearMfa(request)] });
  }

  async function revokeQuietly(request: Request, session: SessionData | null): Promise<void> {
    if (!session || session.accessTokenExpiresAt <= Date.now()) {
      return;
    }
    try {
      await callBackend(config, { method: 'POST', path: '/api/v1/auth/logout', accessToken: session.accessToken, origin: request });
    } catch {
      // Best effort: the backend session still expires on its own.
    }
  }

  return {
    login: (request) =>
      guarded(request, 'POST', async () => {
        const body = await readJsonBody(request);
        const username = stringField(body, 'username', 100);
        const password = stringField(body, 'password', 200);
        const tenantCode = config.kind === 'staff' ? stringField(body, 'tenantCode', 32)?.toLowerCase() : undefined;
        if (!username || !password || (config.kind === 'staff' && (!tenantCode || !TENANT_CODE.test(tenantCode)))) {
          return jsonError(400, 'VALIDATION_FAILED', 'Enter your institution code, username and password');
        }
        // Signing in replaces any session this browser had.
        await revokeQuietly(request, store.read(request));

        const response = await callBackend(config, {
          method: 'POST',
          path: loginPath,
          body: JSON.stringify(config.kind === 'staff' ? { tenantCode, username, password } : { username, password }),
          contentType: 'application/json',
          origin: request,
        });
        const envelope = await readEnvelope<BackendTokenResponse>(response);
        if (!response.ok || !envelope?.success || !envelope.data) {
          return passThroughError(response.status, envelope, store.clear(request));
        }
        const tokens = envelope.data;
        if (tokens.mfaRequired) {
          const expiresAt = Date.parse(tokens.mfaChallengeExpiresAt ?? '');
          if (!tokens.mfaChallengeToken || !Number.isFinite(expiresAt)) {
            return jsonError(502, 'BAD_GATEWAY', 'Unexpected response from the authentication service');
          }
          const result: SignInResult = { mfaRequired: true, passwordChangeRequired: false, mfaEnrollmentRequired: false };
          return jsonOk(result, {
            setCookies: [...store.clear(request), ...store.writeMfa(request, { kind: config.kind, challengeToken: tokens.mfaChallengeToken }, expiresAt)],
          });
        }
        return establish(request, tokens);
      }),

    verifyMfa: (request) =>
      guarded(request, 'POST', async () => {
        const challenge = store.readMfa(request);
        if (!challenge) {
          return jsonError(401, 'MFA_CHALLENGE_EXPIRED', 'The verification step expired. Please sign in again.', {
            setCookies: store.clearMfa(request),
          });
        }
        const code = stringField(await readJsonBody(request), 'code', 6);
        if (!code || !MFA_CODE.test(code)) {
          return jsonError(400, 'VALIDATION_FAILED', 'Enter the 6-digit code from your authenticator app');
        }
        const response = await callBackend(config, {
          method: 'POST',
          path: '/api/v1/auth/mfa/verify',
          body: JSON.stringify({ challengeToken: challenge.challengeToken, code }),
          contentType: 'application/json',
          origin: request,
        });
        const envelope = await readEnvelope<BackendTokenResponse>(response);
        if (!response.ok || !envelope?.success || !envelope.data) {
          return passThroughError(response.status, envelope, []);
        }
        return establish(request, envelope.data);
      }),

    logout: (request) =>
      guarded(request, 'POST', async () => {
        const session = store.read(request);
        if (session) {
          try {
            const fresh = await refresher.ensureFresh(session, request);
            if (fresh.status === 'valid') {
              await callBackend(config, { method: 'POST', path: '/api/v1/auth/logout', accessToken: fresh.session.accessToken, origin: request });
            }
          } catch {
            // The cookies are cleared regardless; the backend session expires on its own.
          }
        }
        return jsonOk({ signedOut: true }, { setCookies: [...store.clear(request), ...store.clearMfa(request)] });
      }),

    session: (request) =>
      guarded(request, 'GET', async () => {
        const session = store.read(request);
        if (!session) {
          return jsonOk({ authenticated: false });
        }
        return jsonOk({
          authenticated: true,
          kind: session.kind,
          passwordChangeRequired: session.passwordChangeRequired,
          mfaEnrollmentRequired: session.mfaEnrollmentRequired,
        });
      }),

    changePassword: (request) =>
      guarded(request, 'POST', async () => {
        const body = await readJsonBody(request);
        const currentPassword = stringField(body, 'currentPassword', 200);
        const newPassword = stringField(body, 'newPassword', 200);
        if (!currentPassword || !newPassword) {
          return jsonError(400, 'VALIDATION_FAILED', 'Enter your current and new password');
        }
        return withSession(request, async (session, setCookies) => {
          const response = await callBackend(config, {
            method: 'POST',
            path: '/api/v1/auth/password',
            accessToken: session.accessToken,
            body: JSON.stringify({ currentPassword, newPassword }),
            contentType: 'application/json',
            origin: request,
          });
          const envelope = await readEnvelope<BackendTokenResponse>(response);
          if (!response.ok || !envelope?.success || !envelope.data) {
            return passThroughError(response.status, envelope, response.status === 401 ? store.clear(request) : setCookies);
          }
          return establish(request, envelope.data);
        });
      }),

    mfaSetup: (request) =>
      guarded(request, 'POST', async () =>
        withSession(request, async (session, setCookies) => {
          const response = await callBackend(config, {
            method: 'POST',
            path: '/api/v1/auth/mfa/totp/setup',
            accessToken: session.accessToken,
            origin: request,
          });
          const envelope = await readEnvelope<{ secret: string; otpauthUri: string }>(response);
          if (!response.ok || !envelope?.success || !envelope.data) {
            return passThroughError(response.status, envelope, setCookies);
          }
          return jsonOk(envelope.data, { setCookies });
        }),
      ),

    mfaActivate: (request) =>
      guarded(request, 'POST', async () => {
        const code = stringField(await readJsonBody(request), 'code', 6);
        if (!code || !MFA_CODE.test(code)) {
          return jsonError(400, 'VALIDATION_FAILED', 'Enter the 6-digit code from your authenticator app');
        }
        return withSession(request, async (session, setCookies) => {
          const response = await callBackend(config, {
            method: 'POST',
            path: '/api/v1/auth/mfa/totp/activate',
            accessToken: session.accessToken,
            body: JSON.stringify({ code }),
            contentType: 'application/json',
            origin: request,
          });
          const envelope = await readEnvelope<BackendTokenResponse>(response);
          if (!response.ok || !envelope?.success || !envelope.data) {
            return passThroughError(response.status, envelope, setCookies);
          }
          return establish(request, envelope.data, session.createdAt);
        });
      }),
  };
}

function passThroughError(
  status: number,
  envelope: { code?: string; message?: string } | null,
  setCookies: string[],
): Response {
  if (status >= 500 || !envelope) {
    return jsonError(status >= 500 ? 503 : 502, 'SERVICE_UNAVAILABLE', 'The service is temporarily unavailable. Please try again.', { setCookies });
  }
  return jsonError(status, envelope.code ?? 'REQUEST_FAILED', envelope.message ?? 'The request failed', { setCookies });
}

async function readJsonBody(request: Request): Promise<Record<string, unknown> | null> {
  const declared = Number.parseInt(request.headers.get('content-length') ?? '0', 10);
  if (declared > MAX_AUTH_BODY_BYTES || !request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) {
    return null;
  }
  const text = await request.text();
  if (text.length > MAX_AUTH_BODY_BYTES) {
    return null;
  }
  try {
    const parsed: unknown = JSON.parse(text);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? (parsed as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function stringField(body: Record<string, unknown> | null, name: string, maxLength: number): string | undefined {
  const value = body?.[name];
  if (typeof value !== 'string') {
    return undefined;
  }
  const trimmed = name.toLowerCase().includes('password') ? value : value.trim();
  return trimmed.length > 0 && trimmed.length <= maxLength ? trimmed : undefined;
}
