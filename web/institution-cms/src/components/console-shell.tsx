'use client';

import { bff, type Me } from '@banking/api';
import { errorMessage, useSignOut } from '@banking/console';
import { Alert, AppShell, Button, Skeleton } from '@banking/ui';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import type { ReactNode } from 'react';
import { MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';

export function ConsoleShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { signOut, signingOut } = useSignOut();
  const me = useQuery({ queryKey: ['me'], queryFn: ({ signal }) => bff<Me>('/me', { signal }), staleTime: 60_000 });

  if (me.isPending) {
    return (
      <div className="grid min-h-dvh lg:grid-cols-[16rem_1fr]" aria-busy="true">
        <div className="hidden bg-sidebar lg:block" />
        <div className="grid content-start gap-4 p-8">
          <Skeleton className="h-8 w-64" />
          <Skeleton className="h-32" />
          <Skeleton className="h-64" />
        </div>
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

  const profile = me.data;
  return (
    <MeProvider me={profile}>
      <AppShell
        brand={<span className="truncate">{profile.institutionName}</span>}
        sections={visibleNavigation(profile.permissions, pathname)}
        link={Link}
        userName={`${profile.firstName} ${profile.lastName}`}
        userDetail={profile.roles.map((role) => role.name).join(', ') || profile.username}
        onSignOut={signOut}
        signingOut={signingOut}
      >
        {children}
      </AppShell>
    </MeProvider>
  );
}
