import { randomUUID } from 'node:crypto';
import type { BffConfig } from './config';

/** Thrown when banking-core can't be reached or doesn't answer in time (as opposed to answering with an error). */
export class BackendUnavailableError extends Error {
  constructor(cause: unknown) {
    super('banking-core is unavailable', { cause });
    this.name = 'BackendUnavailableError';
  }
}

export interface BackendRequest {
  method: string;
  /** Path below the backend root, starting with /api/v1/. */
  path: string;
  accessToken?: string | undefined;
  body?: BodyInit | null | undefined;
  contentType?: string | null | undefined;
  /** The browser request, for forwarding client metadata used in audit logs and rate limiting. */
  origin?: Request | undefined;
  correlationId?: string | undefined;
  /** Idempotency key of a state-changing request, so a retried money movement is processed once (Phase 2). */
  idempotencyKey?: string | undefined;
}

/**
 * Server-to-server call to banking-core. Only the bearer token, content type and client metadata are forwarded; the
 * browser's cookies and other headers never reach the backend.
 */
export async function callBackend(config: BffConfig, request: BackendRequest): Promise<Response> {
  const headers = new Headers({ accept: 'application/json' });
  headers.set('x-correlation-id', request.correlationId ?? randomUUID());
  if (request.accessToken) {
    headers.set('authorization', `Bearer ${request.accessToken}`);
  }
  if (request.idempotencyKey) {
    headers.set('idempotency-key', request.idempotencyKey);
  }
  if (request.contentType) {
    headers.set('content-type', request.contentType);
  }
  const userAgent = request.origin?.headers.get('user-agent');
  if (userAgent) {
    headers.set('user-agent', userAgent.slice(0, 400));
  }
  // The load balancer in front of the BFF appends the real client address; banking-core trusts only the right-most
  // untrusted hop (server.forward-headers-strategy=native), so a client-supplied prefix can't spoof it.
  const forwardedFor = request.origin?.headers.get('x-forwarded-for');
  if (forwardedFor) {
    headers.set('x-forwarded-for', forwardedFor.slice(0, 512));
  }
  try {
    return await fetch(`${config.backendUrl}${request.path}`, {
      method: request.method,
      headers,
      body: request.body ?? undefined,
      redirect: 'manual',
      cache: 'no-store',
      signal: AbortSignal.timeout(config.backendTimeoutMs),
    });
  } catch (error) {
    throw new BackendUnavailableError(error);
  }
}

/** Reads a backend JSON envelope. Returns null when the body isn't JSON. */
export async function readEnvelope<T>(response: Response): Promise<{ success: boolean; data?: T; code?: string; message?: string } | null> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as { success: boolean; data?: T; code?: string; message?: string };
  } catch {
    return null;
  }
}
