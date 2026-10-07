import { createCipheriv, createDecipheriv, createHash, hkdfSync, randomBytes } from 'node:crypto';

/**
 * Authenticated encryption of cookie payloads (AES-256-GCM). The browser can store a sealed value but can neither
 * read nor alter it. Each value is bound to a purpose (associated data), so a sealed MFA challenge can't be replayed
 * as a session, and carries its own expiry, which is enforced here rather than trusting the cookie's Max-Age.
 *
 * Format: v1.<key id>.<iv>.<ciphertext+tag>, all base64url.
 */

const VERSION = 'v1';
const IV_BYTES = 12;
const TAG_BYTES = 16;

interface SealKey {
  id: string;
  key: Buffer;
}

const keyCache = new Map<string, SealKey>();

function deriveKey(secret: string): SealKey {
  const cached = keyCache.get(secret);
  if (cached) {
    return cached;
  }
  const key = Buffer.from(hkdfSync('sha256', secret, 'banking-bff', 'cookie-seal-v1', 32));
  const id = createHash('sha256').update(key).digest('base64url').slice(0, 8);
  const derived = { id, key };
  keyCache.set(secret, derived);
  return derived;
}

interface Envelope<T> {
  exp: number;
  data: T;
}

/** Seals data with the first (current) secret. */
export function seal<T>(data: T, purpose: string, expiresAt: number, secrets: readonly string[]): string {
  const current = secrets[0];
  if (!current) {
    throw new Error('No sealing secret configured');
  }
  const { id, key } = deriveKey(current);
  const iv = randomBytes(IV_BYTES);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(purpose, 'utf8'));
  const plaintext = Buffer.from(JSON.stringify({ exp: expiresAt, data } satisfies Envelope<T>), 'utf8');
  const ciphertext = Buffer.concat([cipher.update(plaintext), cipher.final(), cipher.getAuthTag()]);
  return [VERSION, id, iv.toString('base64url'), ciphertext.toString('base64url')].join('.');
}

/**
 * Opens a sealed value. Returns null for anything that is malformed, tampered with, sealed for another purpose,
 * sealed with an unknown key or expired: callers treat all of those as "no session".
 */
export function unseal<T>(sealed: string | undefined | null, purpose: string, secrets: readonly string[], now = Date.now()): T | null {
  if (!sealed) {
    return null;
  }
  const parts = sealed.split('.');
  if (parts.length !== 4 || parts[0] !== VERSION) {
    return null;
  }
  const [, keyId, ivText, payloadText] = parts as [string, string, string, string];
  const candidate = secrets.map(deriveKey).find((key) => key.id === keyId);
  if (!candidate) {
    return null;
  }
  try {
    const iv = Buffer.from(ivText, 'base64url');
    const payload = Buffer.from(payloadText, 'base64url');
    if (iv.length !== IV_BYTES || payload.length <= TAG_BYTES) {
      return null;
    }
    const decipher = createDecipheriv('aes-256-gcm', candidate.key, iv);
    decipher.setAAD(Buffer.from(purpose, 'utf8'));
    decipher.setAuthTag(payload.subarray(payload.length - TAG_BYTES));
    const plaintext = Buffer.concat([decipher.update(payload.subarray(0, payload.length - TAG_BYTES)), decipher.final()]);
    const envelope = JSON.parse(plaintext.toString('utf8')) as Envelope<T>;
    if (typeof envelope.exp !== 'number' || envelope.exp <= now) {
      return null;
    }
    return envelope.data;
  } catch {
    return null;
  }
}

/** Stable, non-reversible identifier for a secret value (used as an in-memory map key, never logged). */
export function fingerprint(value: string): string {
  return createHash('sha256').update(value, 'utf8').digest('base64url');
}
