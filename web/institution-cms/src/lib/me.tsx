'use client';

import { hasAny, type Me } from '@banking/api';
import { createContext, useContext, type ReactNode } from 'react';

const MeContext = createContext<Me | null>(null);

export function MeProvider({ me, children }: { me: Me; children: ReactNode }) {
  return <MeContext.Provider value={me}>{children}</MeContext.Provider>;
}

/** The signed-in staff member (from GET /api/v1/me). */
export function useMe(): Me {
  const me = useContext(MeContext);
  if (!me) {
    throw new Error('useMe must be used inside the console layout');
  }
  return me;
}

/**
 * Whether the user holds any of the permissions. Used only to hide controls that would fail: banking-core checks the
 * permission again on every request.
 */
export function useCan(...permissions: string[]): boolean {
  return hasAny(useMe().permissions, permissions);
}

/** Renders children only for users holding one of the permissions. */
export function Can({ permission, children, fallback = null }: { permission: string | string[]; children: ReactNode; fallback?: ReactNode }) {
  const allowed = useCan(...(Array.isArray(permission) ? permission : [permission]));
  return <>{allowed ? children : fallback}</>;
}
