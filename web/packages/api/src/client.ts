import type { ApiErrorBody, FieldViolation } from './types';

/**
 * Header the BFF requires on every state-changing request. Browsers never add it to cross-site form posts, so its
 * presence (together with SameSite=Strict cookies) defeats CSRF.
 */
export const BFF_HEADER = 'x-requested-with';
export const BFF_HEADER_VALUE = 'banking-bff';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string | undefined;
  readonly fieldErrors: FieldViolation[];

  constructor(status: number, body: Partial<ApiErrorBody> | null) {
    super(body?.message ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? (status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED');
    this.traceId = body?.traceId;
    this.fieldErrors = body?.errors ?? [];
  }

  /** Message for a field, as reported by the server's validation. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((violation) => violation.field === field)?.message;
  }

  get isUnauthenticated(): boolean {
    return this.status === 401;
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export type UnauthenticatedHandler = (error: ApiError) => void;

let onUnauthenticated: UnauthenticatedHandler | undefined;

/** Lets the app redirect to its login page when the session has ended. */
export function setUnauthenticatedHandler(handler: UnauthenticatedHandler | undefined): void {
  onUnauthenticated = handler;
}

/**
 * Calls the backend through the app's BFF ({@code /api/bff}) and unwraps the response envelope. The browser holds no
 * tokens: the HttpOnly session cookie travels automatically and the BFF adds the bearer token server-side.
 */
export async function bff<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(`/api/bff${path}`, options);
}

/** Calls one of the app's own auth routes ({@code /api/auth/...}). */
export async function authRoute<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(`/api/auth${path}`, options);
}

async function request<T>(url: string, options: RequestOptions): Promise<T> {
  const method = options.method ?? (options.body === undefined ? 'GET' : 'POST');
  const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers: Record<string, string> = { [BFF_HEADER]: BFF_HEADER_VALUE, accept: 'application/json' };
  if (options.body !== undefined && !isForm) {
    headers['content-type'] = 'application/json';
  }
  const response = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
    signal: options.signal,
  });
  const payload = await readJson(response);
  if (!response.ok || (payload && typeof payload === 'object' && 'success' in payload && payload.success === false)) {
    const error = new ApiError(response.status, payload as Partial<ApiErrorBody> | null);
    if (error.isUnauthenticated) {
      onUnauthenticated?.(error);
    }
    throw error;
  }
  if (payload && typeof payload === 'object' && 'data' in payload) {
    return (payload as { data: T }).data;
  }
  return payload as T;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

/** Builds a query string, skipping empty values. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}
