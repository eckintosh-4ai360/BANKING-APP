import { randomUUID } from 'node:crypto';
import { BackendUnavailableError, callBackend } from './backend';
import type { BffConfig } from './config';
import { verifyCsrf } from './csrf';
import { appendCookies, jsonError } from './http';
import type { TokenRefresher } from './refresh';
import type { SessionStore } from './session';

/** Decides which backend paths (below /api/v1/) an app may reach through its proxy. */
export type ProxyRule = (segments: readonly string[], method: string) => boolean;

/** Institution CMS: the tenant API. Never the platform API, and never the auth API (handled by dedicated routes). */
export const institutionRule: ProxyRule = (segments) => segments.length > 0 && segments[0] !== 'platform' && segments[0] !== 'auth';

/** Platform console: the platform API only, except its login (handled by a dedicated route). */
export const platformRule: ProxyRule = (segments) =>
  segments.length > 1 && segments[0] === 'platform' && segments[1] !== 'auth';

const SEGMENT = /^[A-Za-z0-9_~-][A-Za-z0-9._~-]*$/;
const METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']);
const IDEMPOTENCY_KEY = /^[A-Za-z0-9_-]{8,100}$/;
const BODY_TYPES = ['application/json', 'multipart/form-data'];
const PASSED_RESPONSE_HEADERS = ['content-type', 'content-disposition', 'x-correlation-id', 'retry-after', 'idempotency-replayed'];

export interface ProxyDependencies {
  config: BffConfig;
  store: SessionStore;
  refresher: TokenRefresher;
  rule: ProxyRule;
}

/**
 * The only way the browser reaches banking-core. It authenticates the browser by its sealed session cookie, adds the
 * bearer token server-side and passes the request through unchanged otherwise: every business rule, permission and
 * tenant check stays in the backend.
 */
export function createProxy({ config, store, refresher, rule }: ProxyDependencies) {
  return async function proxy(request: Request, segments: readonly string[]): Promise<Response> {
    const method = request.method.toUpperCase();
    if (!METHODS.has(method)) {
      return jsonError(405, 'METHOD_NOT_ALLOWED', 'Method not allowed');
    }
    const csrf = verifyCsrf(request, config);
    if (!csrf.ok) {
      return jsonError(403, 'REQUEST_REJECTED', 'The request was rejected');
    }
    if (!segments.every((segment) => SEGMENT.test(segment)) || !rule(segments, method)) {
      return jsonError(404, 'NOT_FOUND', 'Not found');
    }

    const session = store.read(request);
    if (!session) {
      return jsonError(401, 'UNAUTHENTICATED', 'Your session has ended. Please sign in again.', { setCookies: store.clear(request) });
    }

    let body: ArrayBuffer | undefined;
    const contentType = request.headers.get('content-type');
    if (method !== 'GET' && method !== 'DELETE') {
      const declared = Number.parseInt(request.headers.get('content-length') ?? '0', 10);
      if (declared > config.maxRequestBytes) {
        return jsonError(413, 'PAYLOAD_TOO_LARGE', 'The request is too large');
      }
      body = await request.arrayBuffer();
      if (body.byteLength > config.maxRequestBytes) {
        return jsonError(413, 'PAYLOAD_TOO_LARGE', 'The request is too large');
      }
      if (body.byteLength > 0 && !BODY_TYPES.some((type) => contentType?.toLowerCase().startsWith(type))) {
        return jsonError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Unsupported content type');
      }
    }

    const idempotencyKey = request.headers.get('idempotency-key');
    if (idempotencyKey !== null && !IDEMPOTENCY_KEY.test(idempotencyKey)) {
      return jsonError(400, 'INVALID_IDEMPOTENCY_KEY', 'The idempotency key is malformed');
    }

    const correlationId = randomUUID();
    try {
      const fresh = await refresher.ensureFresh(session, request);
      if (fresh.status === 'expired') {
        return jsonError(401, 'UNAUTHENTICATED', 'Your session has ended. Please sign in again.', { setCookies: store.clear(request) });
      }
      const search = new URL(request.url).search;
      const backendResponse = await callBackend(config, {
        method,
        path: `/api/v1/${segments.join('/')}${search}`,
        accessToken: fresh.session.accessToken,
        body: body && body.byteLength > 0 ? body : undefined,
        contentType: body && body.byteLength > 0 ? contentType : undefined,
        origin: request,
        correlationId,
        idempotencyKey: idempotencyKey ?? undefined,
      });

      const headers = new Headers({ 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' });
      for (const name of PASSED_RESPONSE_HEADERS) {
        const value = backendResponse.headers.get(name);
        if (value) {
          headers.set(name, value);
        }
      }
      const response = new Response(backendResponse.status === 204 ? null : backendResponse.body, {
        status: backendResponse.status,
        headers,
      });
      if (backendResponse.status === 401) {
        // The backend no longer accepts the session (signed out elsewhere, revoked or disabled): drop the cookie.
        return appendCookies(response, store.clear(request));
      }
      return fresh.rotated ? appendCookies(response, store.write(request, fresh.session)) : response;
    } catch (error) {
      // Never log request bodies or tokens: only the route, the failure kind and the correlation id.
      const kind = error instanceof BackendUnavailableError ? 'backend unavailable' : 'token refresh failed';
      console.error(`[bff] ${method} /api/v1/${segments[0] ?? ''}/... ${kind} (correlation ${correlationId})`);
      return jsonError(503, 'SERVICE_UNAVAILABLE', 'The service is temporarily unavailable. Please try again.', {
        headers: { 'x-correlation-id': correlationId },
      });
    }
  };
}
