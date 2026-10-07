import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import type { ReactNode } from 'react';
import { ConsoleShell } from '@/components/console-shell';
import { getBff } from '@/lib/bff';

/**
 * Server-side gate for every console page: no session, no page. This only decides what to render; each API call is
 * still authorised by banking-core.
 */
export default async function ConsoleLayout({ children }: { children: ReactNode }) {
  const cookieStore = await cookies();
  const session = getBff().sessionFromCookies(cookieStore.getAll());
  if (!session) {
    redirect('/login');
  }
  if (session.passwordChangeRequired) {
    redirect('/setup/password');
  }
  if (session.mfaEnrollmentRequired) {
    redirect('/setup/mfa');
  }
  return <ConsoleShell>{children}</ConsoleShell>;
}
