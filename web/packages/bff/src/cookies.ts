/**
 * Minimal cookie handling on standard Request/Response objects, so the BFF runs (and is tested) without Next.js.
 * Large values are split across numbered chunks because browsers cap a single cookie at about 4 KB.
 */

export const CHUNK_SIZE = 3800;
export const MAX_CHUNKS = 5;

export function parseCookies(request: Request): Map<string, string> {
  const result = new Map<string, string>();
  const header = request.headers.get('cookie');
  if (!header) {
    return result;
  }
  for (const part of header.split(';')) {
    const index = part.indexOf('=');
    if (index <= 0) {
      continue;
    }
    const name = part.slice(0, index).trim();
    const value = part.slice(index + 1).trim();
    if (name && !result.has(name)) {
      result.set(name, value);
    }
  }
  return result;
}

export interface CookieOptions {
  secure: boolean;
  maxAgeSeconds: number;
}

export function serializeCookie(name: string, value: string, options: CookieOptions): string {
  const attributes = [`${name}=${value}`, 'Path=/', 'HttpOnly', 'SameSite=Strict', `Max-Age=${Math.max(0, Math.floor(options.maxAgeSeconds))}`];
  if (options.secure) {
    attributes.push('Secure');
  }
  return attributes.join('; ');
}

/** Cookie names get the __Host- prefix when secure: no Domain, Path=/ and Secure are then enforced by the browser. */
export function cookieName(base: string, secure: boolean): string {
  return secure ? `__Host-${base}` : base;
}

/** Reassembles a chunked cookie value; returns undefined when any chunk is missing. */
export function readChunked(cookies: Map<string, string>, name: string): string | undefined {
  const count = Number.parseInt(cookies.get(`${name}.n`) ?? '', 10);
  if (!Number.isInteger(count) || count < 1 || count > MAX_CHUNKS) {
    return undefined;
  }
  let value = '';
  for (let index = 0; index < count; index += 1) {
    const chunk = cookies.get(`${name}.${index}`);
    if (chunk === undefined) {
      return undefined;
    }
    value += chunk;
  }
  return value;
}

/** Set-Cookie headers writing the value in chunks and expiring chunks a previous, longer value left behind. */
export function writeChunked(name: string, value: string, options: CookieOptions, existing: Map<string, string>): string[] {
  const chunks: string[] = [];
  for (let offset = 0; offset < value.length; offset += CHUNK_SIZE) {
    chunks.push(value.slice(offset, offset + CHUNK_SIZE));
  }
  if (chunks.length === 0 || chunks.length > MAX_CHUNKS) {
    throw new Error(`Cookie value does not fit in ${MAX_CHUNKS} chunks`);
  }
  const headers = chunks.map((chunk, index) => serializeCookie(`${name}.${index}`, chunk, options));
  headers.push(serializeCookie(`${name}.n`, String(chunks.length), options));
  for (let index = chunks.length; index < MAX_CHUNKS; index += 1) {
    if (existing.has(`${name}.${index}`)) {
      headers.push(serializeCookie(`${name}.${index}`, '', { ...options, maxAgeSeconds: 0 }));
    }
  }
  return headers;
}

/** Set-Cookie headers deleting every chunk of a value that the request carried. */
export function clearChunked(name: string, options: Omit<CookieOptions, 'maxAgeSeconds'>, existing: Map<string, string>): string[] {
  const headers: string[] = [];
  for (const cookie of existing.keys()) {
    if (cookie === `${name}.n` || new RegExp(`^${escapeRegExp(name)}\\.\\d+$`).test(cookie)) {
      headers.push(serializeCookie(cookie, '', { ...options, maxAgeSeconds: 0 }));
    }
  }
  return headers;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
