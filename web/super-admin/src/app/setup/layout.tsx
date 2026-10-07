import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import type { ReactNode } from 'react';
import { getBff } from '@/lib/bff';

/** Forced account steps (first password, MFA enrolment) need a session, even a restricted one. */
export default async function SetupLayout({ children }: { children: ReactNode }) {
  const cookieStore = await cookies();
  if (!getBff().sessionFromCookies(cookieStore.getAll())) {
    redirect('/login');
  }
  return children;
}
