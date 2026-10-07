import { createAuthHandlers, type AuthHandlers } from './auth';
import { loadBffConfig, type BffConfig, type PrincipalKind } from './config';
import { createProxy, type ProxyRule } from './proxy';
import { TokenRefresher } from './refresh';
import { SessionStore, type SessionData } from './session';

if (typeof window !== 'undefined') {
  throw new Error('@banking/bff is server-only and must never be bundled for the browser');
}

export { loadBffConfig, BffConfigError, type BffConfig, type PrincipalKind } from './config';
export { institutionRule, platformRule, createProxy, type ProxyRule } from './proxy';
export { createAuthHandlers, type AuthHandlers } from './auth';
export { SessionStore, sessionFromTokens, type SessionData, type MfaChallenge, type BackendTokenResponse } from './session';
export { TokenRefresher, REFRESH_MARGIN_MS, ROTATION_GRACE_MS } from './refresh';
export { verifyCsrf } from './csrf';
export { seal, unseal } from './seal';

export interface Bff {
  config: BffConfig;
  store: SessionStore;
  auth: AuthHandlers;
  proxy: (request: Request, segments: readonly string[]) => Promise<Response>;
  /** For server components: the session in a cookie list from next/headers, or null. */
  sessionFromCookies: (cookies: Iterable<{ name: string; value: string }>) => SessionData | null;
}

/** Wires the BFF for one app. Created lazily so configuration is read at runtime, not during `next build`. */
export function createBff(appId: BffConfig['appId'], kind: PrincipalKind, rule: ProxyRule): () => Bff {
  let instance: Bff | undefined;
  return () => {
    if (!instance) {
      const config = loadBffConfig(appId, kind);
      const store = new SessionStore(config);
      const refresher = new TokenRefresher(config);
      instance = {
        config,
        store,
        auth: createAuthHandlers({ config, store, refresher }),
        proxy: createProxy({ config, store, refresher, rule }),
        sessionFromCookies: (cookies) => store.readFromCookies(new Map(Array.from(cookies, (cookie) => [cookie.name, cookie.value]))),
      };
    }
    return instance;
  };
}
