'use client';

import { bff, type PlatformMe } from '@banking/api';
import { errorMessage, useSignOut } from '@banking/console';
import { Alert, AppShell, Button, Skeleton } from '@banking/ui';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { PlatformMeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';

export function PlatformShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { signOut, signingOut } = useSignOut();
  const me = useQuery({ queryKey: ['platform-me'], queryFn: ({ signal }) => bff<PlatformMe>('/platform/me', { signal }), staleTime: 60_000 });

  if (me.isPending) {
    return (
      <div className="grid gap-4 p-8" aria-busy="true">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  if (me.isError) {
    return (
      <div className="mx-auto grid max-w-lg gap-4 p-10">
        <Alert tone="danger" title="The console could not load your profile">
          {errorMessage(me.error)}
        </Alert>
        <div className="flex gap-2">
          <Button onClick={() => me.refetch()}>Try again</Button>
          <Button variant="outline" onClick={signOut} loading={signingOut}>
            Sign out
          </Button>
        </div>
      </div>
    );
  }

  return (
    <PlatformMeProvider me={me.data}>
      <AppShell
        brand="Platform Console"
        sections={visibleNavigation(me.data.permissions, pathname)}
        link={Link}
        userName={me.data.fullName}
        userDetail={me.data.role.replaceAll('_', ' ').toLowerCase()}
        onSignOut={signOut}
        signingOut={signingOut}
        banner={
          <div className="border-b bg-warning-soft px-4 py-2 text-center text-xs lg:px-8">
            Platform operator console. Institution customer and financial data is not accessible here; every action is audited.
          </div>
        }
      >
        {children}
      </AppShell>
    </PlatformMeProvider>
  );
}
