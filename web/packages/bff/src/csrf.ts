import { BFF_HEADER, BFF_HEADER_VALUE } from '@banking/api';
import type { BffConfig } from './config';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export type CsrfVerdict = { ok: true } | { ok: false; reason: string };

/**
 * Cross-site request forgery defence in depth, on top of SameSite=Strict cookies:
 *  1. every BFF request must carry the custom header that only same-origin JavaScript can set without a CORS
 *     preflight (and the BFF answers no preflights);
 *  2. state-changing requests must come from an allowed Origin;
 *  3. when the browser reports Sec-Fetch-Site, it must be same-origin.
 */
export function verifyCsrf(request: Request, config: Pick<BffConfig, 'allowedOrigins'>): CsrfVerdict {
  if (request.headers.get(BFF_HEADER) !== BFF_HEADER_VALUE) {
    return { ok: false, reason: 'missing request header' };
  }
  const fetchSite = request.headers.get('sec-fetch-site');
  if (fetchSite && fetchSite !== 'same-origin') {
    return { ok: false, reason: `sec-fetch-site ${fetchSite}` };
  }
  if (SAFE_METHODS.has(request.method.toUpperCase())) {
    return { ok: true };
  }
  const origin = request.headers.get('origin');
  if (!origin) {
    return { ok: false, reason: 'missing origin' };
  }
  const allowed = config.allowedOrigins.length > 0 ? config.allowedOrigins : [requestOrigin(request)];
  if (!allowed.includes(origin.replace(/\/+$/, ''))) {
    return { ok: false, reason: 'origin not allowed' };
  }
  return { ok: true };
}

/** The origin the request was addressed to (development fallback when APP_ORIGIN isn't configured). */
function requestOrigin(request: Request): string {
  const url = new URL(request.url);
  const host = request.headers.get('host') ?? url.host;
  return `${url.protocol}//${host}`;
}
