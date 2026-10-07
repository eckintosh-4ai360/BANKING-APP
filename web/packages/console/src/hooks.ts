'use client';

import { authRoute } from '@banking/api';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';

/** Signs out through the BFF (which revokes the backend session), clears cached data and goes to the login page. */
export function useSignOut(loginPath = '/login') {
  const queryClient = useQueryClient();
  const [signingOut, setSigningOut] = useState(false);
  const signOut = useCallback(async () => {
    setSigningOut(true);
    try {
      await authRoute('/logout', { method: 'POST' });
    } catch {
      // The BFF clears the cookie even when the backend can't be reached; nothing else to do here.
    } finally {
      queryClient.clear();
      window.location.assign(loginPath);
    }
  }, [loginPath, queryClient]);
  return { signOut, signingOut };
}

export interface SessionInfo {
  authenticated: boolean;
  kind?: 'staff' | 'platform';
  passwordChangeRequired?: boolean;
  mfaEnrollmentRequired?: boolean;
}

/** Where a freshly signed-in (or returning) user should land, given the restrictions on their session. */
export function landingPath(session: Pick<SessionInfo, 'passwordChangeRequired' | 'mfaEnrollmentRequired'>, next: string): string {
  if (session.passwordChangeRequired) {
    return '/setup/password';
  }
  if (session.mfaEnrollmentRequired) {
    return '/setup/mfa';
  }
  return next;
}

export function fetchSession(): Promise<SessionInfo> {
  return authRoute<SessionInfo>('/session');
}
