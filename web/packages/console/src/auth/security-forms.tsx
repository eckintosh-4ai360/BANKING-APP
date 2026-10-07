'use client';

import { authRoute, type MfaSetup, type SignInResult } from '@banking/api';
import { Alert, Button, FormField, Input } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { applyFieldErrors, errorMessage, passwordProblems } from '../errors';

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password').max(128),
    newPassword: z.string().superRefine((value, context) => {
      const problem = passwordProblems(value);
      if (problem) {
        context.addIssue({ code: 'custom', message: problem });
      }
    }),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, { path: ['confirmPassword'], message: 'The passwords do not match' })
  .refine((values) => values.newPassword !== values.currentPassword, { path: ['newPassword'], message: 'Choose a password you have not used' });

type PasswordValues = z.output<typeof passwordSchema>;

export function PasswordChangeForm({ onChanged, submitLabel = 'Change password' }: { onChanged: (result: SignInResult) => void; submitLabel?: string }) {
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof passwordSchema>, unknown, PasswordValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  async function submit(values: PasswordValues) {
    setFailure(undefined);
    try {
      const result = await authRoute<SignInResult>('/password', {
        method: 'POST',
        body: { currentPassword: values.currentPassword, newPassword: values.newPassword },
      });
      form.reset();
      onChanged(result);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError, ['currentPassword', 'newPassword'])) {
        setFailure(errorMessage(error));
      }
    }
  }

  const { errors, isSubmitting } = form.formState;
  return (
    <form className="grid gap-4" onSubmit={form.handleSubmit(submit)} noValidate>
      {failure ? <Alert tone="danger">{failure}</Alert> : null}
      <FormField label="Current password" error={errors.currentPassword?.message} required>
        {(control) => <Input {...control} type="password" autoComplete="current-password" {...form.register('currentPassword')} />}
      </FormField>
      <FormField
        label="New password"
        error={errors.newPassword?.message}
        hint="At least 12 characters. A passphrase of several words works well."
        required
      >
        {(control) => <Input {...control} type="password" autoComplete="new-password" {...form.register('newPassword')} />}
      </FormField>
      <FormField label="Confirm new password" error={errors.confirmPassword?.message} required>
        {(control) => <Input {...control} type="password" autoComplete="new-password" {...form.register('confirmPassword')} />}
      </FormField>
      <Button type="submit" loading={isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  );
}

const codeSchema = z.object({ code: z.string().trim().regex(/^\d{6}$/, 'Enter the 6-digit code') });

/** Authenticator enrolment: start, show the secret for the authenticator app, confirm with a first code. */
export function MfaEnrollment({ onEnabled }: { onEnabled: (result: SignInResult) => void }) {
  const [setup, setSetup] = useState<MfaSetup>();
  const [failure, setFailure] = useState<string>();
  const [starting, setStarting] = useState(false);
  const form = useForm<z.input<typeof codeSchema>, unknown, z.output<typeof codeSchema>>({
    resolver: zodResolver(codeSchema),
    defaultValues: { code: '' },
  });

  async function start() {
    setFailure(undefined);
    setStarting(true);
    try {
      setSetup(await authRoute<MfaSetup>('/mfa/setup', { method: 'POST' }));
    } catch (error) {
      setFailure(errorMessage(error));
    } finally {
      setStarting(false);
    }
  }

  async function activate(values: z.output<typeof codeSchema>) {
    setFailure(undefined);
    try {
      const result = await authRoute<SignInResult>('/mfa/activate', { method: 'POST', body: values });
      setSetup(undefined);
      onEnabled(result);
    } catch (error) {
      form.reset({ code: '' });
      setFailure(errorMessage(error));
    }
  }

  if (!setup) {
    return (
      <div className="grid gap-4">
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <p className="text-sm text-muted-foreground">
          Protect your account with a time-based code from an authenticator app (Google Authenticator, Microsoft
          Authenticator, 1Password and similar).
        </p>
        <div>
          <Button onClick={start} loading={starting}>
            Set up authenticator
          </Button>
        </div>
      </div>
    );
  }

  return (
    <form className="grid gap-4" onSubmit={form.handleSubmit(activate)} noValidate>
      {failure ? <Alert tone="danger">{failure}</Alert> : null}
      <ol className="grid list-decimal gap-3 pl-5 text-sm">
        <li>
          In your authenticator app, add an account and choose to enter a setup key. Use this key (time-based):
          <code className="mt-2 block rounded-md bg-muted px-3 py-2 font-mono text-base tracking-wider break-all select-all">
            {groupKey(setup.secret)}
          </code>
          <span className="mt-1 block text-xs text-muted-foreground">
            On a phone you can instead{' '}
            <a className="text-primary underline" href={setup.otpauthUri}>
              open the setup link
            </a>{' '}
            directly in the authenticator app.
          </span>
        </li>
        <li>Enter the 6-digit code the app shows to confirm.</li>
      </ol>
      <FormField label="Authentication code" error={form.formState.errors.code?.message} required>
        {(control) => <Input {...control} inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="max-w-48 font-mono tracking-[0.4em]" {...form.register('code')} />}
      </FormField>
      <div className="flex gap-2">
        <Button type="submit" loading={form.formState.isSubmitting}>
          Confirm and enable
        </Button>
        <Button variant="ghost" onClick={() => setSetup(undefined)}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

function groupKey(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim();
}
