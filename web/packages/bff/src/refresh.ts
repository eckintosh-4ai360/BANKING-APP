import { callBackend, readEnvelope } from './backend';
import type { BffConfig } from './config';
import { fingerprint } from './seal';
import { sessionFromTokens, type BackendTokenResponse, type SessionData } from './session';

/** Refresh this long before the access token expires, so a request never reaches the backend with a dying token. */
export const REFRESH_MARGIN_MS = 30_000;
/** How long the outcome of a rotation is reused for requests that still carry the previous cookie. */
export const ROTATION_GRACE_MS = 20_000;

export type FreshSession =
  | { status: 'valid'; session: SessionData; rotated: boolean }
  | { status: 'expired' };

/**
 * Keeps sessions' access tokens fresh.
 *
 * Refresh tokens are single-use and the backend revokes the whole session when one is presented twice (theft
 * detection). A page that fires several API calls at once sends the same cookie with each, so:
 *  - concurrent refreshes of the same token share one backend call (single flight), and
 *  - for a short grace period the new session is handed to requests that still carry the old cookie because they
 *    were sent before the browser stored the new one.
 * Both maps are per process. With several BFF instances, enable sticky sessions; without them a parallel request on
 * another instance can trigger reuse detection, which signs the user out (fails safe).
 */
export class TokenRefresher {
  private readonly inFlight = new Map<string, Promise<SessionData | null>>();
  private readonly recent = new Map<string, { session: SessionData; until: number }>();

  constructor(
    private readonly config: BffConfig,
    private readonly clock: () => number = Date.now,
  ) {}

  async ensureFresh(session: SessionData, origin?: Request): Promise<FreshSession> {
    const now = this.clock();
    if (session.accessTokenExpiresAt - now > REFRESH_MARGIN_MS) {
      return { status: 'valid', session, rotated: false };
    }
    if (session.refreshTokenExpiresAt <= now) {
      return { status: 'expired' };
    }
    const refreshed = await this.refresh(session, origin);
    return refreshed ? { status: 'valid', session: refreshed, rotated: true } : { status: 'expired' };
  }

  /** Returns the rotated session, or null when the backend rejected the refresh token. Throws when unreachable. */
  async refresh(session: SessionData, origin?: Request): Promise<SessionData | null> {
    const key = fingerprint(session.refreshToken);
    const now = this.clock();
    this.prune(now);

    const recent = this.recent.get(key);
    if (recent && recent.until > now) {
      return recent.session;
    }
    const pending = this.inFlight.get(key);
    if (pending) {
      return pending;
    }
    const attempt = this.exchange(session, origin)
      .then((result) => {
        if (result) {
          this.recent.set(key, { session: result, until: this.clock() + ROTATION_GRACE_MS });
        }
        return result;
      })
      .finally(() => {
        this.inFlight.delete(key);
      });
    this.inFlight.set(key, attempt);
    return attempt;
  }

  private async exchange(session: SessionData, origin?: Request): Promise<SessionData | null> {
    const response = await callBackend(this.config, {
      method: 'POST',
      path: '/api/v1/auth/token/refresh',
      body: JSON.stringify({ refreshToken: session.refreshToken }),
      contentType: 'application/json',
      origin,
    });
    if (response.status >= 500) {
      throw new Error(`Token refresh failed with status ${response.status}`);
    }
    const envelope = await readEnvelope<BackendTokenResponse>(response);
    if (!response.ok || !envelope?.success || !envelope.data) {
      return null;
    }
    return sessionFromTokens(session.kind, envelope.data, this.clock(), session.createdAt);
  }

  private prune(now: number): void {
    for (const [key, entry] of this.recent) {
      if (entry.until <= now) {
        this.recent.delete(key);
      }
    }
  }
}
