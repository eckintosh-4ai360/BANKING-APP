'use client';

import { ApiError, setUnauthenticatedHandler } from '@banking/api';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';

export interface ConsoleRoutes {
  login: string;
  passwordSetup: string;
  mfaSetup: string;
}

export const DEFAULT_ROUTES: ConsoleRoutes = { login: '/login', passwordSetup: '/setup/password', mfaSetup: '/setup/mfa' };

/** Where the browser must go after an API error that ends or restricts the session, if anywhere. */
export function redirectFor(error: unknown, routes: ConsoleRoutes, currentPath: string): string | null {
  if (!(error instanceof ApiError)) {
    return null;
  }
  if (error.status === 401) {
    return currentPath.startsWith(routes.login) ? null : `${routes.login}?next=${encodeURIComponent(currentPath)}`;
  }
  if (error.code === 'PASSWORD_CHANGE_REQUIRED' && !currentPath.startsWith(routes.passwordSetup)) {
    return routes.passwordSetup;
  }
  if (error.code === 'MFA_ENROLLMENT_REQUIRED' && !currentPath.startsWith(routes.mfaSetup)) {
    return routes.mfaSetup;
  }
  return null;
}

function shouldRetry(failureCount: number, error: unknown): boolean {
  // Client errors won't change on retry; only transient failures of reads are retried.
  if (error instanceof ApiError && error.status < 500) {
    return false;
  }
  return failureCount < 2;
}

function navigateFor(routes: ConsoleRoutes) {
  return (error: unknown) => {
    const target = redirectFor(error, routes, `${window.location.pathname}${window.location.search}`);
    if (target) {
      window.location.assign(target);
    }
  };
}

export function ConsoleProviders({ children, routes = DEFAULT_ROUTES }: { children: ReactNode; routes?: ConsoleRoutes }) {
  const [client] = useState(() => {
    const navigate = navigateFor(routes);
    // 401s are routed by the API client's handler (below) so direct calls are covered too; the caches route the
    // restricted-session errors (password change or MFA enrolment pending).
    const onError = (error: unknown) => {
      if (!(error instanceof ApiError && error.status === 401)) {
        navigate(error);
      }
    };
    return new QueryClient({
      queryCache: new QueryCache({ onError }),
      mutationCache: new MutationCache({ onError }),
      defaultOptions: {
        queries: { staleTime: 30_000, retry: shouldRetry, refetchOnWindowFocus: false },
        // Never retry a mutation automatically: a retried write could duplicate a business action.
        mutations: { retry: false },
      },
    });
  });

  useEffect(() => {
    setUnauthenticatedHandler(navigateFor(routes));
    return () => setUnauthenticatedHandler(undefined);
  }, [routes]);

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
