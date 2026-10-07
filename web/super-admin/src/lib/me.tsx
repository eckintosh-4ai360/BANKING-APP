'use client';

import { hasAny, type PlatformMe } from '@banking/api';
import { createContext, useContext, type ReactNode } from 'react';

const PlatformMeContext = createContext<PlatformMe | null>(null);

export function PlatformMeProvider({ me, children }: { me: PlatformMe; children: ReactNode }) {
  return <PlatformMeContext.Provider value={me}>{children}</PlatformMeContext.Provider>;
}

export function usePlatformMe(): PlatformMe {
  const me = useContext(PlatformMeContext);
  if (!me) {
    throw new Error('usePlatformMe must be used inside the console layout');
  }
  return me;
}

/** UI hint only; banking-core authorises every platform call itself. */
export function useCan(...permissions: string[]): boolean {
  return hasAny(usePlatformMe().permissions, permissions);
}
