'use client';

import { PasswordSetupPage } from '@banking/console';
import { useRouter } from 'next/navigation';

export default function PasswordSetup() {
  const router = useRouter();
  return <PasswordSetupPage navigate={(path) => router.replace(path)} />;
}
