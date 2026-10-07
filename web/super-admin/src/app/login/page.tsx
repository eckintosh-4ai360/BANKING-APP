'use client';

import { LoginPage } from '@banking/console';
import { useRouter } from 'next/navigation';

export default function Login() {
  const router = useRouter();
  return <LoginPage kind="platform" title="Platform sign-in" subtitle="Platform operators only" navigate={(path) => router.replace(path)} />;
}
