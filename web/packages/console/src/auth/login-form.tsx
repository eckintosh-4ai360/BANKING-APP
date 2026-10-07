'use client';

import { authRoute, type SignInResult } from '@banking/api';
import { Alert, Button, FormField, Input } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { errorMessage } from '../errors';

const TENANT_STORAGE_KEY = 'banking.tenantCode';

const staffSchema = z.object({
  tenantCode: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9][a-z0-9-]{1,31}$/, 'Enter your institution code'),
  username: z.string().trim().min(1, 'Enter your username').max(100),
  password: z.string().min(1, 'Enter your password').max(200),
});

const mfaSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, 'Enter the 6-digit code'),
});

export interface LoginFormProps {
  kind: 'staff' | 'platform';
  onSignedIn: (result: SignInResult) => void;
}

/** Credentials step, then (when the account has MFA) the authenticator code step. */
export function LoginForm({ kind, onSignedIn }: LoginFormProps) {
  const [step, setStep] = useState<'credentials' | 'mfa'>('credentials');
  return step === 'credentials' ? (
    <CredentialsStep kind={kind} onMfaRequired={() => setStep('mfa')} onSignedIn={onSignedIn} />
  ) : (
    <MfaStep onSignedIn={onSignedIn} onRestart={() => setStep('credentials')} />
  );
}

type Credentials = z.output<typeof staffSchema>;

function CredentialsStep({ kind, onMfaRequired, onSignedIn }: { kind: LoginFormProps['kind']; onMfaRequired: () => void; onSignedIn: LoginFormProps['onSignedIn'] }) {
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof staffSchema>, unknown, Credentials>({
    resolver: zodResolver(staffSchema),
    // Platform sign-in has no institution; a placeholder satisfies the shared schema and is never sent.
    defaultValues: { tenantCode: kind === 'staff' ? readRememberedTenant() : 'platform', username: '', password: '' },
  });

  async function submit(values: Credentials) {
    setFailure(undefined);
    try {
      const body = kind === 'staff' ? values : { username: values.username, password: values.password };
      const result = await authRoute<SignInResult>('/login', { method: 'POST', body });
      if (kind === 'staff') {
        rememberTenant(values.tenantCode);
      }
      if (result.mfaRequired) {
        onMfaRequired();
      } else {
        onSignedIn(result);
      }
    } catch (error) {
      form.resetField('password');
      setFailure(errorMessage(error, 'Sign-in is unavailable right now. Please try again.'));
    }
  }

  const { errors, isSubmitting } = form.formState;
  return (
    <form className="grid gap-4" onSubmit={form.handleSubmit(submit)} noValidate>
      {failure ? <Alert tone="danger">{failure}</Alert> : null}
      {kind === 'staff' ? (
        <FormField label="Institution code" error={errors.tenantCode?.message} required>
          {(control) => <Input {...control} autoComplete="organization" autoCapitalize="none" spellCheck={false} {...form.register('tenantCode')} />}
        </FormField>
      ) : null}
      <FormField label="Username" error={errors.username?.message} required>
        {(control) => <Input {...control} autoComplete="username" autoCapitalize="none" spellCheck={false} {...form.register('username')} />}
      </FormField>
      <FormField label="Password" error={errors.password?.message} required>
        {(control) => <Input {...control} type="password" autoComplete="current-password" {...form.register('password')} />}
      </FormField>
      <Button type="submit" loading={isSubmitting} className="mt-2 w-full">
        Sign in
      </Button>
    </form>
  );
}

function MfaStep({ onSignedIn, onRestart }: { onSignedIn: LoginFormProps['onSignedIn']; onRestart: () => void }) {
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof mfaSchema>, unknown, z.output<typeof mfaSchema>>({
    resolver: zodResolver(mfaSchema),
    defaultValues: { code: '' },
  });

  async function submit(values: z.output<typeof mfaSchema>) {
    setFailure(undefined);
    try {
      onSignedIn(await authRoute<SignInResult>('/mfa', { method: 'POST', body: values }));
    } catch (error) {
      form.reset({ code: '' });
      setFailure(errorMessage(error, 'Verification is unavailable right now. Please try again.'));
    }
  }

  return (
    <form className="grid gap-4" onSubmit={form.handleSubmit(submit)} noValidate>
      <p className="text-sm text-muted-foreground">Enter the 6-digit code from your authenticator app.</p>
      {failure ? <Alert tone="danger">{failure}</Alert> : null}
      <FormField label="Authentication code" error={form.formState.errors.code?.message} required>
        {(control) => (
          <Input
            {...control}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            autoFocus
            className="text-center font-mono text-lg tracking-[0.5em]"
            {...form.register('code')}
          />
        )}
      </FormField>
      <Button type="submit" loading={form.formState.isSubmitting} className="w-full">
        Verify
      </Button>
      <Button variant="link" onClick={onRestart}>
        Use a different account
      </Button>
    </form>
  );
}

function readRememberedTenant(): string {
  try {
    return typeof window === 'undefined' ? '' : (window.localStorage.getItem(TENANT_STORAGE_KEY) ?? '');
  } catch {
    return '';
  }
}

function rememberTenant(code: string): void {
  try {
    window.localStorage.setItem(TENANT_STORAGE_KEY, code);
  } catch {
    // Storage may be unavailable (private mode); remembering the institution code is only a convenience.
  }
}
