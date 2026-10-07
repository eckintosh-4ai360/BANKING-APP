'use client';

import type { SignInResult } from '@banking/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, Skeleton } from '@banking/ui';
import { useEffect, useState, type ReactNode } from 'react';
import { LoginForm } from '../auth/login-form';
import { MfaEnrollment, PasswordChangeForm } from '../auth/security-forms';
import { safeNextPath } from '../errors';
import { fetchSession, landingPath } from '../hooks';

export function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-muted/40 px-4 py-10">
      <div className="w-full max-w-md">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{title}</CardTitle>
            {subtitle ? <CardDescription>{subtitle}</CardDescription> : null}
          </CardHeader>
          <CardContent className="py-6">{children}</CardContent>
        </Card>
        {footer ? <div className="mt-6 text-center text-xs text-muted-foreground">{footer}</div> : null}
      </div>
    </div>
  );
}

/**
 * Login page body. If the browser already has a session (for example after arriving through an external link, when
 * SameSite=Strict cookies were withheld from the first page load) the user is sent straight on.
 */
export function LoginPage({ kind, title, subtitle, navigate }: { kind: 'staff' | 'platform'; title: string; subtitle?: ReactNode; navigate: (path: string) => void }) {
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let active = true;
    const next = safeNextPath(new URLSearchParams(window.location.search).get('next'));
    fetchSession()
      .then((session) => {
        if (active && session.authenticated) {
          navigate(landingPath(session, next));
          return;
        }
        if (active) {
          setChecking(false);
        }
      })
      .catch(() => active && setChecking(false));
    return () => {
      active = false;
    };
  }, [navigate]);

  function onSignedIn(result: SignInResult) {
    navigate(landingPath(result, safeNextPath(new URLSearchParams(window.location.search).get('next'))));
  }

  return (
    <AuthLayout title={title} subtitle={subtitle} footer="Authorised users only. Activity is logged and monitored.">
      {checking ? (
        <div className="grid gap-4" aria-busy="true">
          <Skeleton className="h-9" />
          <Skeleton className="h-9" />
          <Skeleton className="h-9" />
        </div>
      ) : (
        <LoginForm kind={kind} onSignedIn={onSignedIn} />
      )}
    </AuthLayout>
  );
}

export function PasswordSetupPage({ navigate }: { navigate: (path: string) => void }) {
  return (
    <AuthLayout title="Choose a new password" subtitle="Your password was issued by an administrator or has expired. Choose your own to continue.">
      <PasswordChangeForm submitLabel="Save and continue" onChanged={(result) => navigate(landingPath({ ...result, passwordChangeRequired: false }, '/'))} />
    </AuthLayout>
  );
}

export function MfaSetupPage({ navigate }: { navigate: (path: string) => void }) {
  return (
    <AuthLayout title="Set up two-step verification" subtitle="Your account requires an authenticator app before you can continue.">
      <MfaEnrollment onEnabled={() => navigate('/')} />
    </AuthLayout>
  );
}
