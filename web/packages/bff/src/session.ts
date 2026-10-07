import type { BffConfig, PrincipalKind } from './config';
import { clearChunked, cookieName, parseCookies, readChunked, writeChunked } from './cookies';
import { seal, unseal } from './seal';

/**
 * The server-side view of a signed-in user. It lives only inside the sealed HttpOnly cookie: JavaScript in the browser
 * can't read the tokens, so an XSS bug can't exfiltrate them.
 */
export interface SessionData {
  kind: PrincipalKind;
  accessToken: string;
  /** Epoch milliseconds. */
  accessTokenExpiresAt: number;
  refreshToken: string;
  refreshTokenExpiresAt: number;
  passwordChangeRequired: boolean;
  mfaEnrollmentRequired: boolean;
  createdAt: number;
}

export interface MfaChallenge {
  kind: PrincipalKind;
  challengeToken: string;
}

/** Token response of banking-core (TokenResponse.java). */
export interface BackendTokenResponse {
  tokenType: string | null;
  accessToken: string | null;
  accessTokenExpiresAt: string | null;
  refreshToken: string | null;
  refreshTokenExpiresAt: string | null;
  passwordChangeRequired: boolean;
  mfaEnrollmentRequired: boolean;
  mfaRequired: boolean;
  mfaChallengeToken: string | null;
  mfaChallengeExpiresAt: string | null;
}

export class SessionStore {
  private readonly sessionName: string;
  private readonly mfaName: string;

  constructor(private readonly config: BffConfig) {
    this.sessionName = cookieName(`${config.appId}_session`, config.secureCookies);
    this.mfaName = cookieName(`${config.appId}_mfa`, config.secureCookies);
  }

  private get sessionPurpose(): string {
    return `${this.config.appId}:session`;
  }

  private get mfaPurpose(): string {
    return `${this.config.appId}:mfa`;
  }

  read(request: Request, now = Date.now()): SessionData | null {
    return this.readFromCookies(parseCookies(request), now);
  }

  /** For server components, which see cookies (next/headers) rather than a Request. */
  readFromCookies(cookies: Map<string, string>, now = Date.now()): SessionData | null {
    const session = unseal<SessionData>(readChunked(cookies, this.sessionName), this.sessionPurpose, this.config.sessionSecrets, now);
    if (!session || session.kind !== this.config.kind) {
      return null;
    }
    return session;
  }

  /** Set-Cookie headers storing the session until the refresh token (or the absolute session limit) expires. */
  write(request: Request, session: SessionData, now = Date.now()): string[] {
    const expiresAt = Math.min(session.refreshTokenExpiresAt, session.createdAt + this.config.sessionMaxAgeSeconds * 1000);
    const maxAgeSeconds = Math.max(0, Math.floor((expiresAt - now) / 1000));
    const sealed = seal(session, this.sessionPurpose, expiresAt, this.config.sessionSecrets);
    return writeChunked(this.sessionName, sealed, { secure: this.config.secureCookies, maxAgeSeconds }, parseCookies(request));
  }

  clear(request: Request): string[] {
    return clearChunked(this.sessionName, { secure: this.config.secureCookies }, parseCookies(request));
  }

  readMfa(request: Request, now = Date.now()): MfaChallenge | null {
    const cookies = parseCookies(request);
    const challenge = unseal<MfaChallenge>(readChunked(cookies, this.mfaName), this.mfaPurpose, this.config.sessionSecrets, now);
    if (!challenge || challenge.kind !== this.config.kind) {
      return null;
    }
    return challenge;
  }

  writeMfa(request: Request, challenge: MfaChallenge, expiresAt: number, now = Date.now()): string[] {
    const sealed = seal(challenge, this.mfaPurpose, expiresAt, this.config.sessionSecrets);
    const maxAgeSeconds = Math.max(0, Math.floor((expiresAt - now) / 1000));
    return writeChunked(this.mfaName, sealed, { secure: this.config.secureCookies, maxAgeSeconds }, parseCookies(request));
  }

  clearMfa(request: Request): string[] {
    return clearChunked(this.mfaName, { secure: this.config.secureCookies }, parseCookies(request));
  }
}

/** Builds a session from a token response; returns null when the response carries no complete token pair. */
export function sessionFromTokens(kind: PrincipalKind, tokens: BackendTokenResponse, now = Date.now(), createdAt = now): SessionData | null {
  if (!tokens.accessToken || !tokens.refreshToken || !tokens.accessTokenExpiresAt || !tokens.refreshTokenExpiresAt) {
    return null;
  }
  const accessTokenExpiresAt = Date.parse(tokens.accessTokenExpiresAt);
  const refreshTokenExpiresAt = Date.parse(tokens.refreshTokenExpiresAt);
  if (!Number.isFinite(accessTokenExpiresAt) || !Number.isFinite(refreshTokenExpiresAt) || refreshTokenExpiresAt <= now) {
    return null;
  }
  return {
    kind,
    accessToken: tokens.accessToken,
    accessTokenExpiresAt,
    refreshToken: tokens.refreshToken,
    refreshTokenExpiresAt,
    passwordChangeRequired: tokens.passwordChangeRequired,
    mfaEnrollmentRequired: tokens.mfaEnrollmentRequired,
    createdAt,
  };
}
