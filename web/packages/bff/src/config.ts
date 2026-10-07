/**
 * BFF configuration, read from the server environment at request time (never at build time, so images contain no
 * secrets and one build runs in every environment).
 */

export type PrincipalKind = 'staff' | 'platform';

export interface BffConfig {
  /** Which app this is; part of every cookie name and of the encryption context, so cookies can't move between apps. */
  appId: 'cms' | 'admin';
  kind: PrincipalKind;
  /** banking-core base URL, e.g. http://banking-core:8080 (server-to-server; never exposed to the browser). */
  backendUrl: string;
  /** Session sealing secrets, newest first. Older ones still open existing cookies, enabling rotation. */
  sessionSecrets: string[];
  /** Origins allowed to make state-changing requests. Empty = same origin as the request (development only). */
  allowedOrigins: string[];
  /** Secure cookies with the __Host- prefix. Always true in production. */
  secureCookies: boolean;
  /** Upper bound of a browser session regardless of refresh-token lifetime. */
  sessionMaxAgeSeconds: number;
  backendTimeoutMs: number;
  maxRequestBytes: number;
}

const MIN_SECRET_LENGTH = 32;
const DEVELOPMENT_SECRET = 'development-only-session-secret-change-me-0123456789';

export class BffConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BffConfigError';
  }
}

export interface EnvSource {
  [key: string]: string | undefined;
}

/**
 * Builds the configuration from environment variables. Production refuses to start with a missing or development
 * secret, plain-HTTP cookies or no explicit origin allow-list.
 */
export function loadBffConfig(appId: BffConfig['appId'], kind: PrincipalKind, env: EnvSource = process.env): BffConfig {
  const production = env.NODE_ENV === 'production';
  const backendUrl = (env.BACKEND_URL ?? (production ? '' : 'http://localhost:8080')).replace(/\/+$/, '');
  if (!/^https?:\/\/[^\s/]+/.test(backendUrl)) {
    throw new BffConfigError('BACKEND_URL must be an http(s) URL');
  }

  const rawSecrets = env.SESSION_SECRET ?? (production ? '' : DEVELOPMENT_SECRET);
  const sessionSecrets = rawSecrets
    .split(',')
    .map((secret) => secret.trim())
    .filter((secret) => secret.length > 0);
  if (sessionSecrets.length === 0) {
    throw new BffConfigError('SESSION_SECRET is required');
  }
  if (sessionSecrets.some((secret) => secret.length < MIN_SECRET_LENGTH)) {
    throw new BffConfigError(`SESSION_SECRET values must be at least ${MIN_SECRET_LENGTH} characters`);
  }

  const allowedOrigins = (env.APP_ORIGIN ?? '')
    .split(',')
    .map((origin) => origin.trim().replace(/\/+$/, ''))
    .filter((origin) => origin.length > 0);
  for (const origin of allowedOrigins) {
    if (!/^https?:\/\/[^\s/]+$/.test(origin)) {
      throw new BffConfigError(`APP_ORIGIN entry is not an origin: ${origin}`);
    }
  }

  const secureCookies = env.COOKIE_SECURE ? env.COOKIE_SECURE === 'true' : production;

  if (production) {
    if (sessionSecrets.includes(DEVELOPMENT_SECRET)) {
      throw new BffConfigError('The development session secret must not be used in production');
    }
    if (!secureCookies) {
      throw new BffConfigError('Secure cookies are mandatory in production');
    }
    if (allowedOrigins.length === 0) {
      throw new BffConfigError('APP_ORIGIN is required in production');
    }
    if (allowedOrigins.some((origin) => origin.startsWith('http://'))) {
      throw new BffConfigError('APP_ORIGIN must use https in production');
    }
  }

  return {
    appId,
    kind,
    backendUrl,
    sessionSecrets,
    allowedOrigins,
    secureCookies,
    sessionMaxAgeSeconds: positiveInt(env.SESSION_MAX_AGE_SECONDS, 12 * 60 * 60),
    backendTimeoutMs: positiveInt(env.BACKEND_TIMEOUT_MS, 30_000),
    maxRequestBytes: positiveInt(env.BFF_MAX_REQUEST_BYTES, 11 * 1024 * 1024),
  };
}

function positiveInt(value: string | undefined, fallback: number): number {
  if (!value) {
    return fallback;
  }
  const parsed = Number.parseInt(value, 10);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new BffConfigError(`Expected a positive integer but got "${value}"`);
  }
  return parsed;
}
