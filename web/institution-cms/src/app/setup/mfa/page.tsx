'use client';

import { MfaSetupPage } from '@banking/console';
import { useRouter } from 'next/navigation';

export default function MfaSetup() {
  const router = useRouter();
  return <MfaSetupPage navigate={(path) => router.replace(path)} />;
}
