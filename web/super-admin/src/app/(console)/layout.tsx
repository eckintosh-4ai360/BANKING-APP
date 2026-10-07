import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import type { ReactNode } from 'react';
import { PlatformShell } from '@/components/platform-shell';
import { getBff } from '@/lib/bff';

/** No platform session, no page; restricted sessions finish their password change or MFA enrolment first. */
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
  return <PlatformShell>{children}</PlatformShell>;
}
