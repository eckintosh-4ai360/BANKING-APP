import { ConsoleProviders } from '@banking/console';
import type { Metadata } from 'next';
import { connection } from 'next/server';
import type { ReactNode } from 'react';
import './globals.css';

export const metadata: Metadata = {
  title: { default: 'Institution Console', template: '%s · Institution Console' },
  description: 'Back-office console for financial institutions',
  robots: { index: false, follow: false },
};

/**
 * Every page renders per request: the Content-Security-Policy nonce set in src/proxy.ts can only be applied to
 * dynamically rendered HTML, and a prerendered page would have its scripts blocked.
 */
export default async function RootLayout({ children }: { children: ReactNode }) {
  await connection();
  return (
    <html lang="en">
      <body>
        <ConsoleProviders>{children}</ConsoleProviders>
      </body>
    </html>
  );
}
