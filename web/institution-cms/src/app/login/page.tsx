'use client';

import { LoginPage } from '@banking/console';
import { useRouter } from 'next/navigation';

export default function Login() {
  const router = useRouter();
  return <LoginPage kind="staff" title="Sign in" subtitle="Institution back-office console" navigate={(path) => router.replace(path)} />;
}
